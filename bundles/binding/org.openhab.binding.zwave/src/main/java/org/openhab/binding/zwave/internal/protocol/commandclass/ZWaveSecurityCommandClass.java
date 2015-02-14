/*
 * Copyright (c) 2010-2015, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zwave.internal.protocol.commandclass;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.AbstractQueue;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.openhab.binding.zwave.internal.protocol.NodeStage;
import org.openhab.binding.zwave.internal.protocol.SerialMessage;
import org.openhab.binding.zwave.internal.protocol.SerialMessage.SerialMessageClass;
import org.openhab.binding.zwave.internal.protocol.SerialMessage.SerialMessagePriority;
import org.openhab.binding.zwave.internal.protocol.SerialMessage.SerialMessageType;
import org.openhab.binding.zwave.internal.protocol.ZWaveController;
import org.openhab.binding.zwave.internal.protocol.ZWaveEndpoint;
import org.openhab.binding.zwave.internal.protocol.ZWaveNode;
import org.openhab.binding.zwave.internal.protocol.serialmessage.ApplicationCommandMessageClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.thoughtworks.xstream.annotations.XStreamAlias;

/**
 * Handles the Security command class.
 * 
 * @author Dave Badia
 * @since 1.6.0
 */
@XStreamAlias("securityCommandClass")
public class ZWaveSecurityCommandClass extends ZWaveCommandClass implements ZWaveBasicCommands,
		ZWaveCommandClassInitialization {
	private static final Logger logger = LoggerFactory.getLogger(ZWaveSecurityCommandClass.class);
	/**
	 * How long the device has to respond to nonce requests.  Per spec, min=3, recommended=10, max=20
	 */
	private static final long NONCE_MAX_MILLIS = TimeUnit.SECONDS.toMillis(10);
	private static final byte[] ENCRYPT_PASSWORD = { (byte) 0xAA, (byte) 0xAA, (byte) 0xAA, (byte) 0xAA, (byte) 0xAA,
			(byte) 0xAA, (byte) 0xAA, (byte) 0xAA, (byte) 0xAA, (byte) 0xAA, (byte) 0xAA, (byte) 0xAA, (byte) 0xAA,
			(byte) 0xAA, (byte) 0xAA, (byte) 0xAA };
	private static final byte[] AUTH_PASSWORD = { 0x55, 0x55, 0x55, 0x55, 0x55, 0x55, 0x55, 0x55, 0x55, 0x55, 0x55,
			0x55, 0x55, 0x55, 0x55, 0x55 };
	private static final String AES = "AES";
	private static final int MAC_LENGTH = 8;
	private static final int IV_LENGTH = 16;
	private static final int HALF_OF_IV = IV_LENGTH / 2;
	/**
	 * Marks the end of the list of supported command classes. The remaining classes are those that can be controlled by
	 * the device. These classes are created without values. Messages received cause notification events instead.
	 */
	public static final byte COMMAND_CLASS_MARK = (byte) 0xef;
	/**
	 * The largest amount of payload we can fit into a single {@link SecurityPayload}. {@link SerialMessage} contents
	 * larger than this must be split into mulitple {@link SecurityPayload}
	 */
	private static final int SECURITY_PAYLOAD_ONE_PART_SIZE = 28;

	/**
	 * Request which commands the device supports using 
	 * security encapsulation (encryption)
	 */
	private static final byte SECURITY_COMMANDS_SUPPORTED_GET = 0x02;
	/**
	 * Response from the device which indicates which commans 
	 * the device supports using security encapsulation (encryption)
	 */
	private static final byte SECURITY_COMMANDS_SUPPORTED_REPORT = 0x03;
	/**
	 * Request which security initialization schemes the 
	 * device supports
	 */
	private static final byte SECURITY_SCHEME_GET = 0x04;
	/**
	 * Response from the device of  which security initialization 
	 * schemes the device supports
	 */
	private static final byte SECURITY_SCHEME_REPORT = 0x05;
	/**
	 * The controller is sending the device the network key to
	 * be used for all secure transmissions
	 */
	private static final byte SECURITY_NETWORK_KEY_SET = 0x06;
	/**
	 * Response from the device after getting SECURITY_NETWORK_KEY_SET
	 * that was encapsulated using the new key
	 */
	private static final byte SECURITY_NETWORK_KEY_VERIFY = 0x07;
	/**
	 * Not supported since we are always the master
	 */
	private static final byte SECURITY_SCHEME_INHERIT = 0x08;
	/**
	 * Request to generate a nonce to be used in message encapsulation
	 */
	private static final byte SECURITY_NONCE_GET = 0x40;
	/**
	 * Response with the generated nonce to be used in message encapsulation
	 */
	private static final byte SECURITY_NONCE_REPORT = (byte) 0x80;
	/**
	 * Indicates this message has been encapsulated and must be decrypted
	 * to reveal the actual message
	 * public so {@link ApplicationCommandMessageClass} can check for this and invoke
	 * {@link #decryptMessage(byte[], int)} as needed
	 */
	public static final byte SECURITY_MESSAGE_ENCAP = (byte) 0x81;
	/**
	 * Indicates this message has been encapsulated and must be decrypted
	 * to reveal the actual message and that there are more messages to 
	 * send so another nonce is needed.
	 * public so {@link ApplicationCommandMessageClass} can check for this and invoke
	 * {@link #decryptMessage(byte[], int)} as needed
	 */
	public static final byte SECURITY_MESSAGE_ENCAP_NONCE_GET = (byte) 0xc1;
	
	/**
	 * The order in which commands should be sent and received. 
	 * note that those commands absent from this list ({@link #SECURITY_MESSAGE_ENCAP}
	 * for example) can be sent/received at any time
	 */
	private static final List<Byte> EXPECTED_COMMAND_ORDER_LIST = 
			Arrays.asList(new Byte[]{
		SECURITY_SCHEME_GET,
		SECURITY_SCHEME_REPORT,
		SECURITY_NETWORK_KEY_SET,
		SECURITY_NETWORK_KEY_VERIFY,
		SECURITY_COMMANDS_SUPPORTED_GET,
		SECURITY_COMMANDS_SUPPORTED_REPORT,
	});
	
	private static final boolean HALT_ON_IMPROPER_ORDER = false; // TODO: test with true
	
	/**
	 * Per the z-wave spec, the this scheme is used prior to any keys being negotiated
	 */
	private static final byte SECURITY_SCHEME_ZERO = 0x00;

	private static final List<Byte> REQUIRED_ENCAPSULATION_LIST = 
			Arrays.asList(new Byte[]{
					SECURITY_NETWORK_KEY_VERIFY,
					SECURITY_SCHEME_INHERIT,
					SECURITY_COMMANDS_SUPPORTED_REPORT});
	
	/**
	 * The code from which this was based included numerous bad security practices (hardcoded IVs, seeding of PRNG
	 * with timestamp).
	 * 
	 * It is unknown as to whether that logic was necessary to work around device defects or if it was just by mistake.
	 * 
	 * Setting this to false will use the bad security practices from the original code. true will use accepted security
	 * best practices
	 */
	// TODO; test and set to true permanently after initial tests
	private static final boolean USE_SECURE_CRYPTO_PRACTICES = false;
	private static final boolean DROP_PACKETS_ON_MAC_FAILURE = false; // TODO: set true and maybe remove?
	/**
	 * Security messages are time sensitive so mark them as high priority
	 */
	private static final SerialMessagePriority SECURITY_MESSAGE_PRIORITY = SerialMessagePriority.High;
	/**
	 * Header is made up of 8 bytes for the device's nonce
	 */
	private static final int ENCAPSULATED_HEADER_LENGTH = 8;
	/**
	 * Footer consists of the nonce ID (1 byte) and the MAC (8 bytes)
	 */
	private static final int ENCAPSULATED_FOOTER_LENGTH = 9;

	private final NonceTable nonceTable = new NonceTable();
	/**
	 * Timer to track time elapsed between sending {@link #SECURITY_NONCE_GET} and
	 * receiving {@link #SECURITY_NONCE_REPORT} 
	 */
	private final NonceTimer requestNonceTimer = new NonceTimer();
	private AbstractQueue<SecurityPayload> payloadQueue = new ConcurrentLinkedQueue<>();
	private AtomicBoolean waitingForNonce = new AtomicBoolean(false);
	/**
	 *	Every <b>set</b> of multi frame messages must have unique sequence number.
	 */
	private byte sequenceCounter = 0;
	private boolean networkKeySet = false;
	private boolean schemeAgreed = false;
	/**
	 * TODO: what is the purpose of this?
	 */
	private boolean secured = false;

	/**
	 * The network key as configured in the openhab.cfg -> zwave:networkey
	 */
	private static SecretKey realNetworkKey;
	/**
	 * The error that occurred when trying to load the encryption key from openhab.cfg -> zwave:networkey
	 * Will be null if the load succeeded
	 */
	private static IllegalArgumentException keyException; // TODO: check this
	
	/**
	 * The network key currently in use.  My be {@link #realNetworkKey} or a scheme network key
	 */
	private SecretKey networkKey;
	/**
	 * The encryption key currently in use which is derived from {@link #networkKey}
	 */
	private SecretKey encryptKey;
	/**
	 * The auth key currently in use which is derived from {@link #networkKey}
	 */
	private SecretKey authKey;

	private byte currentState = EXPECTED_COMMAND_ORDER_LIST.get(0);
	
	/**
	 * Creates a new instance of the ZWaveThermostatFanModeCommandClass class.
	 * 
	 * @param node
	 *            the node this command class belongs to
	 * @param controller
	 *            the controller to use
	 * @param endpoint
	 *            the endpoint this Command class belongs to
	 */
	public ZWaveSecurityCommandClass(ZWaveNode node, ZWaveController controller, ZWaveEndpoint endpoint) {
		super(node, controller, endpoint);
		setupNetworkKey();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public CommandClass getCommandClass() {
		return CommandClass.SECURITY;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getMaxVersion() {
		return 1;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void handleApplicationCommandRequest(SerialMessage serialMessage, int offset, int endpoint) {
		logger.debug("NODE {}: Received Security Request", this.getNode().getNodeId());
		byte command = (byte) serialMessage.getMessagePayloadByte(offset);
		if(REQUIRED_ENCAPSULATION_LIST.contains((byte) command) && !serialMessage.wasEncapsulated()) {
			logger.error("NODE {}: Command should have been encapsulated but wasn't! {}", serialMessage);
			return;
		}
		if(!verifyAndAdvanceStage(command)) {
			return;
		}
		debugHex("payload bytes for incoming security message", serialMessage.getMessagePayload());
		switch (command) {
		case SECURITY_COMMANDS_SUPPORTED_REPORT:
			/*
			 * this is a list of CommandClasses that should be Encrypted. and it might contain new command classes that
			 * were not present in the NodeInfoFrame so we have to run through, mark existing Command Classes as
			 * SetSecured (so SendMsg in the Driver class will route the unecrypted messages to our SendMsg) and for New
			 * Command Classes, create them, and of course, also do a SetSecured on them.
			 * 
			 * This means we must do a SecurityCmd_SupportedGet request ASAP so we dont have Command Classes created
			 * after the Discovery Phase is completed!
			 */
			handleSupportedReport(serialMessage.getMessagePayload());
			return;

		case SECURITY_SCHEME_REPORT:
			int schemes = serialMessage.getMessagePayloadByte(offset + 1);
			logger.debug("NODE {}: Received Security Scheme Report: ", this.getNode().getNodeId(), schemes);
			if (schemeAgreed) {
				logger.debug("NODE {}: Already received a Security Scheme Report, ignoring", this.getNode().getNodeId());
			} else if (schemes == SECURITY_SCHEME_ZERO) {
				// We're good to go. We now should send our NetworkKey to the device if this is the first time we have
				// seen it
				logger.debug("NODE {}: Security scheme agreed.");
				// create the NetworkKey Packet. queueMessageForEncapsulation will request a nonce then encrypt it for us
				SerialMessage message = new SerialMessage(this.getNode().getNodeId(), SerialMessageClass.SendData,
						SerialMessageType.Request, SerialMessageClass.SendData, SECURITY_MESSAGE_PRIORITY);
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				baos.write((byte) this.getNode().getNodeId());
				baos.write(18);
				baos.write((byte) getCommandClass().getKey());
				baos.write(SECURITY_NETWORK_KEY_SET);
				try {
					baos.write(networkKey.getEncoded());
					message.setMessagePayload(baos.toByteArray());
					queueMessageForEncapsulation(message);
					schemeAgreed = true;
				} catch (IOException e) {
					logger.error("NODE {}: IOException trying to write SECURITY_NETWORK_KEY_SET", e);
				}
			} else {
				// No common security scheme. The device should continue as an unsecured node. but some Command Classes
				// might not be present...
				logger.error("NODE {}: No common security scheme.  The device will continue as an unsecured node.  " +
						"Scheme requested was {}", this.getNode().getNodeId(), schemes);
			}
			return;

		case SECURITY_NETWORK_KEY_SET:
			// we shouldn't get a NetworkKeySet from a node if we are the controller as we send it out to the Devices
			logger.info("NODE {}: Received SECURITY_NETWORK_KEY_SET from node but we shouldn't have gotten it.", this
					.getNode().getNodeId());
			return;

		case SECURITY_NETWORK_KEY_VERIFY:
			// if we can decrypt this packet, then we are assured that our SECURITY_NETWORK_KEY_SET is successful and
			// thus should set the Flag referenced in SECURITY_SCHEME_REPORT
			// now as for our SupportedGet
			SerialMessage result = new SerialMessage(this.getNode().getNodeId(), SerialMessageClass.SendData,
					SerialMessageType.Request, SerialMessageClass.SendData, SECURITY_MESSAGE_PRIORITY);
			byte[] payload = { 
					(byte) this.getNode().getNodeId(), 
					2, 
					(byte) getCommandClass().getKey(),
					SECURITY_COMMANDS_SUPPORTED_GET, 
			};
			result.setMessagePayload(payload);
			this.getController().sendData(result);
			return;

		case SECURITY_SCHEME_INHERIT:
			//  only used in a controller replication type environment.
			logger.info("NODE {}: Received SECURITY_SCHEME_INHERIT from node but it's not supported: {}", this
					.getNode().getNodeId(), serialMessage);
			return;

		case SECURITY_NONCE_GET:
			// the Device wants to send us a Encrypted Packet, and thus requesting for our latest NONCE
			sendNonceReport();
			return;

		case SECURITY_NONCE_REPORT:
			// we received a NONCE from a device, so assume that there is something in a queue to send out
			// Nonce is messageBuf without the first offset +1 bytes
			byte[] messageBuf = serialMessage.getMessagePayload();
			int startAt = offset + 1;
			int copyCount = messageBuf.length - startAt;
			byte[] nonce = new byte[copyCount];
			System.arraycopy(messageBuf, startAt, nonce, 0, copyCount);
			waitingForNonce.set(false);
			sendNextMessageWithNonce(nonce);
			return;

		case SECURITY_MESSAGE_ENCAP:
			// SECURITY_MESSAGE_ENCAP should be caught and handled in {@link ApplicationCommandMessageClass}
			logger.warn("NODE {}: Received SECURITY_MESSAGE_ENCAP in ZWaveSecurityCommandClass which should not happen: {}.", 
					this.getNode().getNodeId(), serialMessage);
			return;

		case SECURITY_MESSAGE_ENCAP_NONCE_GET:
			// SECURITY_MESSAGE_ENCAP_NONCE_GET should be caught and handled in {@link ApplicationCommandMessageClass}
			logger.warn("NODE {}: Received SECURITY_MESSAGE_ENCAP_NONCE_GET in ZWaveSecurityCommandClass which should not happen: {}.", 
					this.getNode().getNodeId(), serialMessage);
			return;

		default:
			logger.warn(String.format("NODE %s: Unsupported Command 0x%02X for command class %s (0x%02X) for message %s.", 
					this.getNode().getNodeId(), command, this.getCommandClass().getLabel(),
					this.getCommandClass().getKey(), serialMessage));
			new Exception().printStackTrace(); // TODO: remove
		}
	}

	/**
	 * Decrypts a security encapsulated message from the Z-Wave network
	 * @param offset the offset at which to start processing data
	 * 
	 * @param endpoint
	 * @param messagePayload
	 */
	public SerialMessage decryptMessage(byte[] data, int offset) {
		ByteArrayInputStream bais = new ByteArrayInputStream(data);
		// check for minimum size here so we can ignore the return value of bais.read() below
		int minimumSize = offset + ENCAPSULATED_HEADER_LENGTH + 1 + ENCAPSULATED_FOOTER_LENGTH;
		if(data.length < minimumSize) {
			logger.error("NODE {}: Dropping encapsulated packet which is too small:  min={}, actual={}", 
					this.getNode().getNodeId(), minimumSize, data.length);
			return null;
		}
		try {
			// advance past offset since those bytes were already processed
			bais.read(new byte[offset]);
			byte[] initializationVector = new byte[IV_LENGTH];
			// the next 8 bytes of packet are the nonce generated by the device for the IV
			bais.read(initializationVector, 0, HALF_OF_IV);
			int ciphertextSize = data.length - offset - ENCAPSULATED_HEADER_LENGTH - ENCAPSULATED_FOOTER_LENGTH;
			// Next are the ciphertext bytes
			byte[] ciphertextBytes = new byte[ciphertextSize];
			bais.read(ciphertextBytes);
			logger.info("NODE {}: Encrypted Packet Sizes: total={}, encrypted={}", this.getNode().getNodeId(), data.length,
					ciphertextSize);
			debugHex("ciphertextBytes", ciphertextBytes);
			// Get the nonce id so we can populate the 2nd half of the IV
			byte nonceId = (byte) bais.read();
			if (USE_SECURE_CRYPTO_PRACTICES) {
				Nonce nonce = nonceTable.getNonceById(nonceId);
				if(nonce == null) {
					logger.error("NODE {}: Could not find nonce (probably expired) for id={} in table={}", 
							this.getNode().getNodeId(), nonceId, nonceTable.table);
					return null;
				} 
				System.arraycopy(nonce.getNonceBytes(), 0, initializationVector, HALF_OF_IV, HALF_OF_IV);
			} else {
				byte[] insecureNonce = new byte[HALF_OF_IV];
				Arrays.fill(insecureNonce, (byte) 0xAA);
				System.arraycopy(insecureNonce, 0, initializationVector, HALF_OF_IV, HALF_OF_IV);
			}
			debugHex("IV", initializationVector);
			byte[] macFromPacket = new byte[MAC_LENGTH];
			bais.read(macFromPacket);
			Cipher cipher = Cipher.getInstance("AES/OFB/NoPadding"); // TODO remove all comments like this after
																		// testing: remove aes_ofb_encrypt(plaintextmsg,
																		// encryptedpayload, payload->m_length+1,
																		// initializationVector, this->EncryptKey)
			cipher.init(Cipher.DECRYPT_MODE, encryptKey, new IvParameterSpec(initializationVector));
			byte[] plaintextBytes = cipher.doFinal(ciphertextBytes);
			debugHex("plaintextBytes", plaintextBytes);

			byte driverNodeId = (byte) this.getController().getOwnNodeId();
			byte[] mac = generateMACComplex(data, data.length, (byte) this.getNode().getNodeId(), driverNodeId,
					initializationVector);
			// this->GenerateAuthentication(_data, _length, GetNodeId(), GetDriver()->GetNodeId(), iv, mac);
			if (!Arrays.equals(mac, macFromPacket)) {
				logger.error("NODE {}: MAC Authentication of packet failed. dropping", this.getNode().getNodeId());
				debugHex("full packet", data);
				debugHex("package mac", macFromPacket);
				debugHex("our mac", mac);
				if (DROP_PACKETS_ON_MAC_FAILURE) {
					if (payloadQueue.size() > 0) {
						requestNonce();
					}
					return null;
				} else {
					logger.error("NODE {}: Just kidding, ignored failed MAC Authentication of packet", this.getNode()
							.getNodeId());
				}
			}
			/*
			 * XXX TODO: Check the Sequence Header Frame to see if this is the first part of a message, or 2nd part, or
			 * a entire message.
			 * 
			 * I havn't actually seen a Z-Wave Message thats too big to fit in a encrypted message yet, so we will look
			 * at this if such a message actually exists!
			 */
			if (payloadQueue.size() > 0)
				requestNonce(); // handle the next one
			if (!secured) {
				secured = true;
			}
			SerialMessage decryptedMessage = new SerialMessage(plaintextBytes);
			decryptedMessage.setWasEncapsulated(true);
			return decryptedMessage;
		} catch (Exception e) {
			logger.error("Error decrypting packet", e);
			return null;
		}
	}

	public void sendNonceReport() {
		byte[] newNonce = nonceTable.generateNewNonce().getNonceBytes();
		if (!USE_SECURE_CRYPTO_PRACTICES) {
			newNonce = new byte[HALF_OF_IV];
			Arrays.fill(newNonce, (byte) 0xAA);
		}

		SerialMessage message = new SerialMessage(this.getNode().getNodeId(), SerialMessageClass.SendData,
				SerialMessageType.Request, SerialMessageClass.SendData, SECURITY_MESSAGE_PRIORITY);
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		baos.write((byte) this.getNode().getNodeId());
		baos.write((byte) 10);
		baos.write((byte) getCommandClass().getKey());
		baos.write(SECURITY_NONCE_REPORT);
		try {
			baos.write(newNonce);
			message.setMessagePayload(baos.toByteArray());
			// TODO: OZW seems to append our flags to each message, does controller do that too?
			message.setTransmitOptions(ZWaveController.TRANSMIT_OPTION_ACK | ZWaveController.TRANSMIT_OPTION_AUTO_ROUTE);
			this.getController().sendData(message);
		} catch (IOException e) {
			logger.error("NODE {}: Error during Security sendNextMessageWithNonce.", e);
		}
	}

	/**
	 * Queues the given message for encapsulation (encryption) and transmission.
	 * 
	 * Note that, per the z-wave spec, we don't just encrypt the message and send it. We need to first request a nonce
	 * from the node, wait for that response, then encrypt and send. Therefore this message will be placed into a queue
	 * until the next nonce is received. Only then will it be encrypted and sent.
	 * 
	 * It also possible that the message will get split into multiple parts
	 * 
	 * @param message
	 *            the unencrypted message to be transmitted
	 */
	public void queueMessageForEncapsulation(SerialMessage serialMessage) {
		if (serialMessage.getMessageBuffer().length < 7) {
			logger.error("NODE {}: Message too short for encapsulation, dropping message {}", this.getController()
					.getNode(serialMessage.getMessageNode()).getNodeId(), serialMessage);
			return;
		}

		if (serialMessage.getMessageClass() != SerialMessageClass.SendData) {
			logger.error(String.format("Invalid message class %s (0x%02X) for sendData for message %s", serialMessage
					.getMessageClass().getLabel(), serialMessage.getMessageClass().getKey(), serialMessage.toString()));
		}

		// Start with command class byte, so strip off node and length
		int copyLength = serialMessage.getMessagePayload().length - 2;
		byte[] payloadBuffer = new byte[copyLength];
		System.arraycopy(serialMessage.getMessagePayload(), 2, payloadBuffer, 0, copyLength);
		if (payloadBuffer.length > SECURITY_PAYLOAD_ONE_PART_SIZE) {
			// Message must be split into two parts
			queuePayloadForTransmission(new SecurityPayload(1, 2, payloadBuffer, 0, SECURITY_PAYLOAD_ONE_PART_SIZE,
					serialMessage.toString()));
			int part2Length = payloadBuffer.length - SECURITY_PAYLOAD_ONE_PART_SIZE;
			queuePayloadForTransmission(new SecurityPayload(2, 2, payloadBuffer, SECURITY_PAYLOAD_ONE_PART_SIZE, part2Length,
					serialMessage.toString()));
		} else {
			// The entire message can be encapsulated as one
			queuePayloadForTransmission(new SecurityPayload(1, 1, payloadBuffer, 0, payloadBuffer.length,
					serialMessage.toString()));
		}
	}

	/**
	 * Queue a {@link SecurityPayload} to be encapsulated (encrypted) on receipt of a nonce value from the remote node.
	 * 
	 * @param securityPayload
	 *            the payload to be encapsulated (encrypted)
	 */
	private void queuePayloadForTransmission(SecurityPayload securityPayload) {
		// payloadQueue itself is thread safe, but the OZW code locks on waiting for nonce
		// and requestNonce() as well, so we should too
		synchronized (payloadQueue) {
			payloadQueue.add(securityPayload);
			if (!waitingForNonce.get()) {
				// Request a nonce from the node. Its arrival
				// will trigger the sending of the first payload
				requestNonce();
			}
		}
	}

	/**
	 * Gets the next message from {@link #payloadQueue}, encapsulates (encrypts) it, then transmits
	 * 
	 * @param deviceNonce
	 *            the nonce from the device which is used as the 2nd half of the IV
	 */
	private void sendNextMessageWithNonce(byte deviceNonce[]) {
		if (requestNonceTimer.isExpired()) {
			// The nonce was not received within the alloted time of us sending the nonce request. Send it again
			logger.debug("NODE {}: nonce was not received within 10 seconds, resending reques.", this.getNode()
					.getNodeId());
			requestNonce();
			return;
		}

		debugHex("device nonce for next message send", deviceNonce);
		// Fetch the next payload from the queue and encapsulate it
		SecurityPayload securityPayload = payloadQueue.poll();
		if (securityPayload == null) {
			logger.trace("NODE {}: payloadQueue was empty, returning.", this.getNode().getNodeId());
			return;
		}

		// Encapsulate the message fragment
		logger.debug("NODE {}: SECURITY_MESSAGE_ENCAP ({}).", this.getNode().getNodeId(),
				securityPayload.getLogMessage());
		debugHex("SecurityPayloadBytes", securityPayload.getMessageBytes());
		// MessageEncapNonceGet doesn't seem to work
		// GetNodeId(), REQUEST, FUNC_ID_ZW_SEND_DATA, true);
		SerialMessage message = new SerialMessage(this.getNode().getNodeId(), SerialMessageClass.SendData,
				SerialMessageType.Request, SerialMessageClass.SendData, SECURITY_MESSAGE_PRIORITY);
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		baos.write(securityPayload.getLength() + 20); // msg->Append( payload->m_length + 20 );
		baos.write(this.getCommandClass().getKey()); // msg->Append(GetCommandClassId());
		// TODO: commented out in OZW code: msg->Append( (queueSize>1) ? SecurityCmd_MessageEncapNonceGet :
		// SecurityCmd_MessageEncap );
		byte commandClass = SECURITY_MESSAGE_ENCAP;
		baos.write(commandClass); // msg->Append( SecurityCmd_MessageEncap );
		// TODO: use SECURITY_MESSAGE_ENCAP_NONCE_GET when appropriate, add timer since OZW says it doesn't work
		
		// create the iv
		byte[] initializationVector = new byte[16];
		if (USE_SECURE_CRYPTO_PRACTICES) {
			byte[] nonceBytes = nonceTable.generateNewNonce().getNonceBytes();
			// Generate a new nonce.  Fill the entire thing as the 2nd half will be overwritten below
			System.arraycopy(nonceBytes, 0, initializationVector, 0, HALF_OF_IV);
		} else {
			// Fill the entire thing as the 2nd half will be overwritten below
			Arrays.fill(initializationVector, (byte) 0xAA);
		}
		// the 2nd half of the IV is the nonce provided by the device
		System.arraycopy(deviceNonce, 0, initializationVector, HALF_OF_IV, HALF_OF_IV);

		try {
			/*
			 * Append the first 8 bytes of the initialization vector to the message. The remaining 8 bytes are the NONCE
			 * we received from the node, and is committed from sending back to the Node. But we use the full 16 bytes of
			 * the IV to encrypt our message.
			 */
			baos.write(initializationVector, 0, HALF_OF_IV);

			/*
			 *  Append the sequence data in a single byte.  The entire byte is zero if the whole
			 *  message fit into one frame.  If multiple frames are required:
			 *   1st 2 bits: 	reserved, always 0
			 *   3rd bit: 		second frame: 0 for 1st frame, 1 for second frame
			 *   4th bit:		sequenced: 	0 if the entire message fits in one frame; 1 if more than 1 are required
			 *   last 4 bits:	sequence counter - used to tell groups of sequenced messages apart.  
			 *   					Must be the same for part 1 and part 2 of a sequenced message
			 */
			byte sequenceByte = 0; // Remains 0 if entire message fit in one frame
			int totalParts = securityPayload.getTotalParts();
			if(totalParts < 1 || totalParts > 2) {
				logger.error("NODE {}: securityPayload had invalid number of parts: {}   aborted send.", 
						this.getNode().getNodeId(), totalParts);
				return;
			}
			// nothing to do if entire message fit in one frame
			if(totalParts > 1) {
				sequenceByte = (byte) ((sequenceCounter) & 0x0f);
				if (securityPayload.getPart() == 1) {
					sequenceByte |= 0x10; // Sequenced, first frame
				} else if (securityPayload.getPart() == 2) {
					sequenceByte |= 0x30; // Sequenced, second frame
					// Increment sequenceCounter for the next payload
					sequenceCounter++;
				}
			}
			// at most, the payload will be 28 bytes + 1 byte for the sequence byte
			byte[] plaintextMessageBytes = new byte[1 + securityPayload.getLength()];
			plaintextMessageBytes[0] = sequenceByte;
			System.arraycopy(securityPayload.getMessageBytes(), 0, plaintextMessageBytes, 1,
					securityPayload.getLength());
			// Append the message payload after encrypting it with AES-OFB (key is EncryptPassword,
			// full IV (16 bytes - 8 Random and 8 NONCE) and payload
			debugHex("Input frame fro encryption:", plaintextMessageBytes);
			debugHex("IV:", initializationVector);

			// This will use hardware AES acceleration when possible (default in JDK 8)
			Cipher encryptCipher = Cipher.getInstance("AES/OFB/NoPadding"); // TODO remove all comments like this after
																			// initializationVector, this->EncryptKey)
			encryptCipher.init(Cipher.ENCRYPT_MODE, encryptKey, new IvParameterSpec(initializationVector));
			byte[] ciphertextBytes = encryptCipher.doFinal(plaintextMessageBytes);
			debugHex("Encrypted Output", ciphertextBytes);
			baos.write(ciphertextBytes);
			// Append the nonce identifier which is the first byte of the device nonce
			baos.write(deviceNonce[0]);
			int commandClassByteOffset = 2;
			int toMacLength = baos.toByteArray().length - commandClassByteOffset; // Start at command class byte
			byte[] toMac = new byte[toMacLength];
			System.arraycopy(baos.toByteArray(), commandClassByteOffset, toMac, 0, toMacLength);
			// Generate the MAC
			byte sendingNode = (byte) this.getController().getOwnNodeId();
			byte[] mac = generateMAC(commandClass, ciphertextBytes, sendingNode, (byte) getNode().getNodeId(),
					initializationVector);
			debugHex("Auth mac", mac);
			baos.write(mac);
			byte[] payload = baos.toByteArray();
			debugHex("Outgoing encrypted message", payload);
			message.setMessagePayload(payload);
			this.getController().sendData(message);

			// finally, if the message we just sent is a NetworkKeySet, then we need to reset our Network Key here
			// as the reply we will get back will be encrypted with the new Network key
			if (!networkKeySet && bytesAreEqual(securityPayload.getMessageBytes()[0], 0x98)
					&& bytesAreEqual(securityPayload.getMessageBytes()[1], 0x06)) {
				// if ((this->m_networkkeyset == false) && (payload->m_data[0] == 0x98) && (payload->m_data[1] == 0x06))
				// {
				logger.info("NODE {}: Reseting Network Key after Inclusion", this.getNode().getNodeId());
				networkKeySet = true;
				setupNetworkKey();
			}
		} catch (GeneralSecurityException | IOException e) {
			logger.error("NODE {}: Error in sendNextMessageWithNonce, message not sent", e);
		}
	}

	private void setupNetworkKey() {
		boolean isAddingNode = this.getNode().getNodeStage() == NodeStage.DETAILS;
		logger.info("NODE {}: addingNode={} stage={})", this.getNode(),	isAddingNode, this.getNode().getNodeStage());
	
		if (isAddingNode && !networkKeySet) {
			logger.info("NODE {}: Using Scheme0 Network Key for Key Exchange addingNode={} KeySet={})",
					this.getNode(), isAddingNode, networkKeySet);
			// Scheme0 network key is a key of all zeros
			networkKey = new SecretKeySpec(new byte[16], AES);
		} else {
			logger.info("NODE {}: Using Configured Network Key addingNode={} keySet={})", this.getNode(), isAddingNode,
					networkKeySet);
			networkKey = realNetworkKey;
		}
		debugHex("Network Key", networkKey.getEncoded());

		try {
			// Derived the message encryption key from the xnetwork key
			Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
			cipher.init(Cipher.ENCRYPT_MODE, networkKey); // aes_ecb_encrypt(iv, tmpauth, 16, this->AuthKey
			encryptKey = new SecretKeySpec(cipher.doFinal(ENCRYPT_PASSWORD), AES);
			debugHex("Encrypt Key", encryptKey.getEncoded());

			// Derived the message auth key from the network key
			cipher.init(Cipher.ENCRYPT_MODE, networkKey); // aes_ecb_encrypt(iv, tmpauth, 16, this->AuthKey
			authKey = new SecretKeySpec(cipher.doFinal(AUTH_PASSWORD), AES);
			debugHex("Auth Key", authKey.getEncoded());
		} catch (GeneralSecurityException e) {
			logger.error("NODE {}: Error building derived keys", e);
		}
	}

	/**
	 * This starts the security registration process between us and the node so 
	 * we can communicate securely from here forward.  Specifically, the 
	 * following commands are exchanged:  
	 * 
	 * {@value #EXPECTED_COMMAND_ORDER_LIST}
	 */
	public void securityInit() {
		// if we are adding this node, then send SECURITY_SCHEME_GET which
		// will start the Network Key Exchange
		boolean isAddingNode = true; // TODO: get this from wherever we get it from above
		if (isAddingNode) {
			SerialMessage message = new SerialMessage(this.getNode().getNodeId(), SerialMessageClass.SendData,
					SerialMessageType.Request, SerialMessageClass.SendData, SECURITY_MESSAGE_PRIORITY);
			byte[] payload = { 
					(byte) this.getNode().getNodeId(), 
					3, 
					(byte) getCommandClass().getKey(),
					SECURITY_SCHEME_GET, 
					0 
			};
			// SchemeGet is unencrypted
			message.setMessagePayload(payload);
			this.getController().sendData(message);
		} else {
			SerialMessage message = new SerialMessage(this.getNode().getNodeId(), SerialMessageClass.SendData,
					SerialMessageType.Request, SerialMessageClass.SendData, SECURITY_MESSAGE_PRIORITY);
			byte[] payload = {
					(byte) this.getNode().getNodeId(), 
					2, 
					(byte) getCommandClass().getKey(),
					SECURITY_COMMANDS_SUPPORTED_GET, 
			};
			message.setMessagePayload(payload);
		}
	}

	/**
	 * Sends a message to the node requesting a new nonce so we can encapsulate (encrypt) and send our next
	 * {@link SecurityPayload} from {@link #payloadQueue}
	 */
	private synchronized void requestNonce() {
		if (waitingForNonce.get()) {
			return;
		}
		waitingForNonce.set(true); // now we are
		// Msg* msg = new Msg( "SecurityCmd_NonceGet", GetNodeId(), REQUEST, FUNC_ID_ZW_SEND_DATA, true, true,
		// FUNC_ID_APPLICATION_COMMAND_HANDLER, GetCommandClassId() );
		SerialMessage result = new SerialMessage(this.getNode().getNodeId(), SerialMessageClass.SendData,
				SerialMessageType.Request, SerialMessageClass.SendData, SECURITY_MESSAGE_PRIORITY);
		byte[] payload = { 
				(byte) this.getNode().getNodeId(), 
				2, 
				(byte) getCommandClass().getKey(), 
				SECURITY_NONCE_GET,
		};
		result.setTransmitOptions(ZWaveController.TRANSMIT_OPTION_ACK | ZWaveController.TRANSMIT_OPTION_AUTO_ROUTE); 
		result.setMessagePayload(payload);
		this.getController().sendData(result);

		// Reset the nonce timer. The nonce report must be received within 10 seconds.
		requestNonceTimer.reset();
	}

	/**
	 * Generate the MAC (message authentication code) from a security-encrypted message
	 * 
	 * @throws GeneralSecurityException
	 */
	byte[] generateMAC(byte commandClass, byte[] ciphertext, byte sendingNode, byte receivingNode, byte[] iv)
			throws GeneralSecurityException {
		// TODO: change all error to debug
		debugHex("BAD ciphertext", ciphertext);
		debugHex("BAD iv", iv);
		// Build a buffer containing a 4-byte header and the encrypted message data, padded with zeros to a 16-byte
		// boundary.
		byte[] buffer = new byte[256]; // uint8 buffer[256]; TODO: set this accurately
		byte[] tempAuth = new byte[16]; // uint8 tmpauth[16];

		buffer[0] = commandClass;
		buffer[1] = sendingNode;
		buffer[2] = receivingNode;
		// TODO: validate data size?
		buffer[3] = (byte) ciphertext.length;
		System.arraycopy(ciphertext, 0, buffer, 4, ciphertext.length);
		int bufferSize = ciphertext.length + 4; // the size of the buffer TODO: what? why + 4?
		debugHex("NetworkKey", networkKey.getEncoded(), 0, 16);
		debugHex("Raw Auth (minus IV)", buffer, 0, bufferSize);
		logger.debug("NODE {}: Raw Auth (Minus IV) Size:{} ({})", bufferSize, bufferSize + 16);

		// Encrypt the IV with ECB
		Cipher encryptCipher = Cipher.getInstance("AES/ECB/NoPadding"); // TODO remove all comments like this after
		encryptCipher.init(Cipher.ENCRYPT_MODE, authKey); // aes_ecb_encrypt(iv, tmpauth, 16, this->AuthKey
		tempAuth = encryptCipher.doFinal(iv);
		debugHex("BAD tmp1", tempAuth);
		// our temporary holding
		byte[] encpck = new byte[16]; // TODO rename this uint8 encpck[16];
		int block = 0;

		// now xor the buffer with our encrypted IV
		for (int i = 0; i < bufferSize; i++) {
			encpck[block] = buffer[i];
			block++;
			// if we hit a blocksize, then encrypt TODO: encrypt? looks like xor to me...
			if (block == 16) {
				for (int j = 0; j < 16; j++) {
					debugHex("BAD prexor tmp", tempAuth);
					debugHex("BAD prexor encpck", encpck);
					// here we do our xor
					tempAuth[j] = (byte) (encpck[j] ^ tempAuth[j]);
					encpck[j] = 0;
				}
				debugHex("BAD postxor tmp", tempAuth);
				debugHex("BAD postxor encpck", encpck);
				// reset encpck for good measure
				Arrays.fill(encpck, (byte) 0);
				// reset our block counter back to 0
				block = 0;

				encryptCipher.init(Cipher.ENCRYPT_MODE, authKey); 
				tempAuth = encryptCipher.doFinal(tempAuth); // if (aes_ecb_encrypt(tmpauth, tmpauth, 16, this->AuthKey)
				debugHex("BAD tmp2", tempAuth);										// == EXIT_FAILURE) {
			}
		}
		debugHex("BAD afterxor tempAuth", tempAuth);	
		debugHex("BAD afterxor encpck", encpck);	
		logger.error("NODE {}: block:{} ", block); // TODO: debug

		// any left over data that isn't a full block size
		if (block > 0) {
			for (int i = 0; i < 16; i++) {
				// encpck from block to 16 is already guaranteed to be 0 so its safe to xor it with out tmpmac
				debugHex("BAD prexor2 tmp", tempAuth);
				debugHex("BAD prexor2 encpck", encpck);
				tempAuth[i] = (byte) (encpck[i] ^ tempAuth[i]);
			}

			
			encryptCipher.init(Cipher.ENCRYPT_MODE, authKey); // aes_mode_reset(this->AuthKey);
			tempAuth = encryptCipher.doFinal(tempAuth); // (aes_ecb_encrypt(tmpauth, tmpauth, 16, this->AuthKey)
		}
		/* we only care about the first 8 bytes of tmpauth as the mac */
		debugHex("Computed Auth", tempAuth);
		byte[] mac = new byte[8];
		System.arraycopy(tempAuth, 0, mac, 0, 8);
		return mac;
	}

	/**
	 * Complex as in hard to understand what's going on
	 * @deprecated use generateMAC instead
	 */
	byte[] generateMACComplex(byte[] data, int length, byte sendingNode, byte receivingNode, byte[] iv)
			throws GeneralSecurityException {
		// TODO: change all error to debug
		debugHex("BAD data", data);
		debugHex("BAD iv", iv);
		// Build a buffer containing a 4-byte header and the encrypted message data, padded with zeros to a 16-byte
		// boundary.
		byte[] buffer = new byte[256]; // uint8 buffer[256];
		byte[] tempAuth = new byte[16]; // uint8 tmpauth[16];

		buffer[0] = data[0]; // Security command class command // buffer[0] = _data[0];
		buffer[1] = sendingNode;
		buffer[2] = receivingNode;
		// TODO: validate data size?
		byte copyLength = (byte) (length - 19); // Subtract 19 to account for the 9 security command class bytes that
												// come before and after the encrypted data
		buffer[3] = copyLength;
		System.arraycopy(data, 9, buffer, 4, copyLength); // Copy the cipher bytes over //memcpy( &buffer[4], &_data[9],
															// _length-19 ); // Encrypted message

		int bufferSize = copyLength + 4; // the size of the buffer TODO: what? why + 4?
		debugHex("Raw Auth (minus IV)", buffer, 0, bufferSize);
		logger.debug("NODE {}: Raw Auth (Minus IV) Size:{} ({})", bufferSize, bufferSize + 16);

		// Encrypt the IV with ECB
		Cipher encryptCipher = Cipher.getInstance("AES/ECB/NoPadding"); // TODO remove all comments like this after
		encryptCipher.init(Cipher.ENCRYPT_MODE, authKey); // aes_ecb_encrypt(iv, tmpauth, 16, this->AuthKey
		tempAuth = encryptCipher.doFinal(iv);
		debugHex("BAD tmp1", tempAuth);
		// our temporary holding
		byte[] encpck = new byte[16]; // TODO rename this uint8 encpck[16];
		int block = 0;

		// now xor the buffer with our encrypted IV
		for (int i = 0; i < bufferSize; i++) {
			encpck[block] = buffer[i];
			block++;
			// if we hit a blocksize, then encrypt TODO: encrypt? looks like xor to me...
			if (block == 16) {
				debugHex("BAD prexor tmp", tempAuth);
				for (int j = 0; j < 16; j++) {
					// here we do our xor
					tempAuth[j] = (byte) (encpck[j] ^ tempAuth[j]);
					encpck[j] = 0;
				}
				debugHex("BAD prexor tmp", tempAuth);
				debugHex("BAD prexor encpck", encpck);
				// reset encpck for good measure
				Arrays.fill(encpck, (byte) 0);
				// reset our block counter back to 0
				block = 0;

				encryptCipher.init(Cipher.ENCRYPT_MODE, authKey); 
				tempAuth = encryptCipher.doFinal(tempAuth); // if (aes_ecb_encrypt(tmpauth, tmpauth, 16, this->AuthKey)
				debugHex("BAD tmp2", tempAuth);										// == EXIT_FAILURE) {
			}
		}
		debugHex("BAD afterxor tempAuth", tempAuth);	
		debugHex("BAD afterxor encpck", encpck);	
		logger.error("NODE {}: block:{} ", block); // TODO: debug

		// any left over data that isn't a full block size
		if (block > 0) {
			for (int i = 0; i < 16; i++) {
				// encpck from block to 16 is already guaranteed to be 0 so its safe to xor it with out tmpmac
				debugHex("BAD prexor2 tmp", tempAuth);
				debugHex("BAD prexor2 encpck", encpck);
				tempAuth[i] = (byte) (encpck[i] ^ tempAuth[i]);
			}

			
			encryptCipher.init(Cipher.ENCRYPT_MODE, authKey); // aes_mode_reset(this->AuthKey);
			tempAuth = encryptCipher.doFinal(tempAuth); // (aes_ecb_encrypt(tmpauth, tmpauth, 16, this->AuthKey)
		}
		/* we only care about the first 8 bytes of tmpauth as the mac */
		debugHex("Computed Auth", tempAuth);
		byte[] mac = new byte[8];
		System.arraycopy(tempAuth, 0, mac, 0, 8);
		return mac;
	}

	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public Collection<SerialMessage> initialize() {
		return Collections.EMPTY_LIST;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public SerialMessage getValueMessage() {
		logger.debug("NODE {}: Creating new message for application command TODO", this.getNode().getNodeId());
		// TODO: what to do, OZW does nothing but return true;
		return null;
	}

	/**
	 * Gets a SerialMessage with the TODO command
	 * 
	 * @return the serial message, or null if the supported command is not supported.
	 */
	public SerialMessage getSupportedMessage() {
		logger.debug("NODE {}: Creating new message for application command TODO", this.getNode().getNodeId());
		// TODO: what to do, OZW does nothing but return true;
		return null;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public SerialMessage setValueMessage(int value) {
		// TODO: what to do, OZW doesn't do this
		return null;
	}

	private void handleSupportedReport(byte[] messagePayload) {
		debugHex("Security Classes", messagePayload, 2, messagePayload.length);
		getNode().setSecuredClasses(messagePayload);
		// We're done all of our NodeStage#SECURITY_REPORT stuff, advance
		getNode().advanceNodeStage(NodeStage.getNodeStage(NodeStage.SECURITY_REPORT.getStage() + 1));
	}

	/**
	 * Utility method to do unsigned byte comparison. This is necessary since in java all primitives are signed but
	 * zwave we often represent values in hex (which is unsigned).
	 * 
	 * @param aByte
	 *            a byte
	 * @param anotherByte
	 *            an int
	 * @return true if they are equal
	 */
	public static boolean bytesAreEqual(byte aByte, int anotherByte) {
		return aByte == ((byte) (anotherByte & 0xff));
	}
	
	/**
	 * Since these operations are security sensitive we must ensure they are
	 * executing in the proper sequence
	 * @param newStage the state we are about to enter
	 * @return true if the new command was in an acceptable order, false
	 * if it was not.  if false is returned, the response should <b>not</b>
	 * be sent.
	 */
	private synchronized boolean verifyAndAdvanceStage(byte newStage) {
		if(!EXPECTED_COMMAND_ORDER_LIST.contains(newStage)) {
			// Commands absent from EXPECTED_COMMAND_ORDER_LIST are always ok
			return true;
		}
		// Going back to the first step (zero indec) is always OK
		if(EXPECTED_COMMAND_ORDER_LIST.indexOf(newStage) > 0) {
			// We have to verify where we are at
			int currentIndex = EXPECTED_COMMAND_ORDER_LIST.indexOf(currentState);
			int newIndex = EXPECTED_COMMAND_ORDER_LIST.indexOf(newStage);
			if(newIndex - currentIndex != 1) {
				if(HALT_ON_IMPROPER_ORDER) {
					logger.error("NODE {}: Commands received out of order, aborting current={}, new={}", 
							this.getNode().getNodeId(), currentIndex, newIndex);
					return false;
				} else {
					logger.warn("NODE {}: Commands received out of order (warning only) current={}, new={}", 
							this.getNode().getNodeId(), currentIndex, newIndex);
					// fall through below
				}
			}
		}
		currentState = newStage;
		return true;
	}

	public static void setRealNetworkKey(String hexString) {
		String ourString = hexString.replace(",", "");
		ourString = ourString.replace(" ", "");
		ourString = ourString.replace("0x", "");
		try {
			byte[] keyBytes = javax.xml.bind.DatatypeConverter.parseHexBinary(ourString);
			ZWaveSecurityCommandClass.realNetworkKey = new SecretKeySpec(keyBytes, "AES");
			logger.info("Update networkKey");
		} catch (IllegalArgumentException e) {
			logger.error("Error parsing zwave:networkKey", e);
			ZWaveSecurityCommandClass.keyException = e;
		}
	}

	/**
	 * Utility method to dump a byte array as hex. Will only print the data if debug mode is debug logging is actually
	 * enabled
	 * 
	 * @param description
	 *            a human readable description of the data being logged
	 * @param bytes
	 *            the bytes to convert to hex and log
	 * @param offset
	 *            where to start from; zero means log the full byte array
	 */
	private void debugHex(String description, byte[] bytes, int offset, int length) {
		// TODO: use SerialMesage.bb2hex instead?
		if (!logger.isErrorEnabled()) { // TODO: change to debug
			return;
		}
		StringBuilder buf = new StringBuilder();
		for (int i = offset; i < length; i++) {
			buf.append(String.format("0x%02x, ", (bytes[i] & 0xff)));
		}
		logger.error("{}={}", description, buf.toString()); // TODO: change to debug
	}

	/**
	 * Utility method to dump a byte array as hex. Will only print the data if debug mode is debug logging is actually
	 * enabled
	 * 
	 * @param description
	 *            a human readable description of the data being logged
	 * @param bytes
	 *            the bytes to convert to hex and log
	 */
	private void debugHex(String description, byte[] messagePayload) {
		debugHex(description, messagePayload, 0, messagePayload.length);
	}

	private static class SecurityPayload {
		private final int partNumber;
		private final int totalParts;
		private final byte[] partBytes;
		private final int length;
		private final String logMessage;

		public SecurityPayload(int partNumber, int totalParts, byte[] messageBuffer, int offset, int length, String logMessage) {
			this.partNumber = partNumber;
			this.totalParts = totalParts;
			this.length = length;
			this.partBytes = new byte[length];
			System.arraycopy(messageBuffer, offset, partBytes, 0, length);
			if (messageBuffer.length > SECURITY_PAYLOAD_ONE_PART_SIZE) {
				totalParts = 2;
			}
			this.logMessage = "SecurityPayload (part " + partNumber + " of " + totalParts + "): " + logMessage;
		}

		public int getTotalParts() {
			return totalParts;
		}

		public String getLogMessage() {
			return logMessage;
		}

		public byte[] getMessageBytes() {
			return partBytes;
		}

		public int getPart() {
			return partNumber;
		}

		// TODO: remove, just let them use length of byte[]
		public int getLength() {
			return length;
		}
	}

	/**
	 * per the spec we must track how long it has been since we
	 * sent a nonce and only allow it's use within a specified
	 * time period.  
	 */
	private static class NonceTimer {
		private long start = System.currentTimeMillis();

		private void reset() {
			start = System.currentTimeMillis();
		}

		private boolean isExpired() {
			return System.currentTimeMillis() > (start + NONCE_MAX_MILLIS);
		}
	}
	
	/**
	 * Structure to hold the nonce itself and the it's related data
	 */
	private static class Nonce {
		private final byte[] nonceBytes;
		private final NonceTimer timer;
		private final byte nonceId;
		
		private Nonce(byte[] nonceBytes, NonceTimer timer) {
			super();
			this.nonceBytes = nonceBytes;
			this.timer = timer;
			this.nonceId = nonceBytes[0];
		}

		private byte[] getNonceBytes() {
			return nonceBytes;
		}

		private NonceTimer getTimer() {
			return timer;
		}

		private byte getNonceId() {
			return nonceId;
		}
	}

	/**
	 * Data store to hold the nonces we have generated and
	 * provide a method to cleanup old nonces
	 *
	 */
	private class NonceTable {
		private final SecureRandom secureRandom = new SecureRandom();
		private Map<Byte, Nonce> table = new ConcurrentHashMap<>();

		private NonceTable() {
			super();
			// TODO: seed secure random?
		}
		
		private Nonce generateNewNonce() {
			cleanup();
			byte[] nonceBytes = new byte[8]; 
			secureRandom.nextBytes(nonceBytes);
			// Make sure the id is unique for all currently valid nonces
			while(getNonceById(nonceBytes[0]) != null) {
				secureRandom.nextBytes(nonceBytes);
			}
			Nonce nonce = new Nonce(nonceBytes, new NonceTimer());
			table.put(nonce.getNonceId(), nonce);
			return nonce;
		}
		
		private Nonce getNonceById(byte id) {
			cleanup();
			return table.get(id);
		}
		
		/**
		 * Remove any expired nonces from our table
		 */
		private void cleanup() {
			Iterator<Entry<Byte, Nonce>> iter = table.entrySet().iterator();
			while(iter.hasNext()) {
				Nonce nonce = iter.next().getValue();
				if(nonce.getTimer().isExpired()) {
					logger.warn("NODE {}: Expiring nonce with id={}", 
							ZWaveSecurityCommandClass.this.getNode().getNodeId(), nonce.getNonceId()); // TODO: hex
					iter.remove();
				}
			}
		}
	}
}
