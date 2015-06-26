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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TCPSimple network communications connector. Maintains the IP connection and
 * reconnects on error. If responses stop, the connection is reconnected.
 * 
 * @author Chris Jackson
 * @since 1.7.0
 */
public class TCPSimpleConnectorTCPServer extends TCPSimpleConnector {

	private static final Logger logger = LoggerFactory
			.getLogger(TCPSimpleConnectorTCPServer.class);

	private int ipPort;

	ServerSocket socket;
	
	Thread inputThread = null;

	public TCPSimpleConnectorTCPServer() {
	}

	public void connect(String address, int port) throws IOException {
		ipPort = port;

		updateLastReceive();

		doConnect();
	}

	private void doConnect() throws IOException {
		socket = new ServerSocket(ipPort);
		
		/*
		try {
			socket = new Socket(ipAddress, ipPort);
			in = socket.getInputStream();
			out = socket.getOutputStream();
		} catch (UnknownHostException e) {
			logger.error("Can't find host: {}:{}.", ipAddress, ipPort);
		} catch (IOException e) {
			logger.error("Couldn't get I/O for the connection to: {}:{}.",
					ipAddress, ipPort);
			
			return;
		}
*/
		inputThread = new InputReader();
		inputThread.start();
	}

	public void disconnect() {
		if (socket == null)
			return;

		logger.debug("Interrupt connection");
		inputThread.interrupt();

		logger.debug("Close connection");

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

	public class InputReader extends Thread {
		InputStream in;

		public void interrupt() {
			super.interrupt();
			try {
				in.close();
			} catch (IOException e) {
				logger.error("Error reading TCPSimple connection: ",
						e.getMessage());
			} // quietly close
		}

		public void run() {
			String rxData = "";
			try {
				while(true)          {
					Socket connectionSocket = socket.accept();
					BufferedReader inFromClient = new BufferedReader(new InputStreamReader(connectionSocket.getInputStream()));
					String sentence = inFromClient.readLine();         

					updateLastReceive();
	
					for (char ch : sentence.toCharArray()) {
						// Ignore CR
						if (ch == '\r' || ch == '\n') {
							continue;
						}
	
						rxData += ch;
	
						// Process new line
						if (ch == '>') {
							sendToListeners(rxData);
							rxData = "";
						}
						else {
							if(rxData.length() > 2048) {
								rxData = "";
							}
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
