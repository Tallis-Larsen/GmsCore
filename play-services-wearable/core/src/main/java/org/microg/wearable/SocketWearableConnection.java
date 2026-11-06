/*
 * SPDX-FileCopyrightText: 2015, microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */

package org.microg.wearable;

import org.microg.wearable.proto.MessagePiece;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;

public class SocketWearableConnection extends WearableConnection {
    private final int MAX_PIECE_SIZE = 20 * 1024 * 1024;
    private final Closeable socket;
    private final DataInputStream is;
    private final DataOutputStream os;

    public SocketWearableConnection(Socket socket, Listener listener) throws IOException {
        this(socket.getInputStream(), socket.getOutputStream(), socket, listener);
    }

    public SocketWearableConnection(InputStream is, OutputStream os, Closeable socket, Listener listener) {
        super(listener);
        this.socket = socket;
        this.is = new DataInputStream(is);
        this.os = new DataOutputStream(os);
    }

    protected void writeMessagePiece(MessagePiece piece) throws IOException {
        byte[] bytes = piece.encode();
        os.writeInt(bytes.length);
        os.write(bytes);
    }

    protected MessagePiece readMessagePiece() throws IOException {
        int len = is.readInt();
        if (len > MAX_PIECE_SIZE) {
            throw new IOException("Piece size " + len + " exceeded limit of " + MAX_PIECE_SIZE + " bytes.");
        }
        System.out.println("Reading piece of length " + len);
        byte[] bytes = new byte[len];
        is.readFully(bytes);
        return MessagePiece.ADAPTER.decode(bytes);
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}
