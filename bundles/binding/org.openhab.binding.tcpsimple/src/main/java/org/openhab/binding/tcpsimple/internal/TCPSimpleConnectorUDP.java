/**
 * openHAB, the open Home Automation Bus.
 * Copyright (C) 2010-2013, openHAB.org <admin@openhab.org>
 *
 * See the contributors.txt file in the distribution for a
 * full listing of individual contributors.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see <http://www.gnu.org/licenses>.
 *
 * Additional permission under GNU GPL version 3 section 7
 *
 * If you modify this Program, or any covered work, by linking or
 * combining it with Eclipse (or a modified version of that library),
 * containing parts covered by the terms of the Eclipse Public License
 * (EPL), the licensors of this Program grant you additional permission
 * to convey the resulting work.
 */
package org.openhab.binding.tcpsimple.internal;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

//import javax.xml.bind.DatatypeConverter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TCPSimple network communications connector. Maintains the IP connection and
 * reconnects on error. If responses stop, the connection is reconnected.
 * 
 * @author Chris Jackson
 * @since 1.7.0
 */
public class TCPSimpleConnectorUDP extends TCPSimpleConnector {

	private static final Logger logger = LoggerFactory
			.getLogger(TCPSimpleConnectorUDP.class);

	private List<TCPSimpleEventListener> _listeners = new ArrayList<TCPSimpleEventListener>();

	private int ipPort;

	private DatagramSocket socket = null;
	
	private long lastReceive;

	Thread inputThread = null;

	public TCPSimpleConnectorUDP() {
	}

	public void connect(String address, int port) throws IOException {
		ipPort = port;

		// Initialise the last receive time to avoid immediate reconnect
		lastReceive = System.currentTimeMillis();

		doConnect();
	}

	private void doConnect() throws IOException {
		try {
			socket = new DatagramSocket(ipPort);
		} catch (IOException e) {
			logger.error("Couldn't get I/O for the connection to: port {}.", ipPort);

			return;
		}

		inputThread = new InputReader(this);
		inputThread.start();
	}

	public void disconnect() {
		if (socket == null) {
			return;
		}
		
		socket.close();

		logger.debug("Interrupt connection");
		inputThread.interrupt();

		socket = null;
		inputThread = null;

		logger.debug("Ready");
	}

	public void sendMessage(byte[] data) {
/*		if (socket == null) {
			logger.debug("TCPSimple disconnected: Performing reconnect");
			try {
				doConnect();
			} catch (IOException e) {
				logger.error("Error reconnecting Heatmiser");
			}
		}

		if (socket == null)
			return;

		try {
			out.write(data);
			out.flush();
		} catch (IOException e) {
			logger.error("TCPSimple: Error sending message " + e.getMessage());
			disconnect();
		}*/
	}
	
	public long getLastReceive() {
		return lastReceive;
	}

	public class InputReader extends Thread {
		TCPSimpleConnectorUDP connector;

		public InputReader(TCPSimpleConnectorUDP connector) {
			this.connector = connector;
		}

		public void interrupt() {
			super.interrupt();
			if(socket != null) {
				socket.close();
			}
		}

		public void run() {
			final int dataBufferMaxLen = 1024;

			String rxData = "";
			byte[] dataBuffer = new byte[dataBufferMaxLen];

			DatagramPacket receivePacket = new DatagramPacket(dataBuffer, dataBuffer.length);
			try {
				socket.receive(receivePacket);

				String sentence = new String( receivePacket.getData(), 0, receivePacket.getLength() );
				logger.debug("Received: {}", sentence);

				lastReceive = System.currentTimeMillis();

				for (char ch : sentence.toCharArray()) {
					// Ignore CR
					if (ch == '\r') {
						continue;
					}

					// Process new line
					if (ch == '\n') {
						try {
							TCPSimpleResponseEvent event = new TCPSimpleResponseEvent(connector);

							Iterator<TCPSimpleEventListener> iterator = _listeners.iterator();
							while (iterator.hasNext()) {
								((TCPSimpleEventListener) iterator.next()).packetReceived(event, rxData);
							}
						} catch (Exception e) {
							logger.error("Event listener error", e);
						}
						rxData = "";
					}
					else {
						rxData += ch;
						if(rxData.length() > 1024) {
							rxData = "";
						}
					}
				}
			} catch (InterruptedIOException e) {
				Thread.currentThread().interrupt();
				logger.error("Interrupted via InterruptedIOException");
			} catch (IOException e) {
				logger.error("Reading from network failed", e);
			}

			logger.debug("Ready reading from network");
		}
	}
}
