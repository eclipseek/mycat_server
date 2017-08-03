/*
 * Copyright (c) 2013, OpenCloudDB/MyCAT and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software;Designed and Developed mainly by many Chinese 
 * opensource volunteers. you can redistribute it and/or modify it under the 
 * terms of the GNU General Public License version 2 only, as published by the
 * Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 * 
 * Any questions about this component can be directed to it's project Web address 
 * https://code.google.com/p/opencloudb/.
 *
 */
package io.mycat.net.mysql;

import java.nio.ByteBuffer;

import io.mycat.backend.mysql.BufferUtil;
import io.mycat.backend.mysql.MySQLMessage;
import io.mycat.net.FrontendConnection;

/**
 * From server to client during initial handshake.
 * 
 * <pre>
 * Bytes                        Name
 * -----                        ----
 * 1                            protocol_version
 * n (Null-Terminated String)   server_version
 * 4                            thread_id
 * 8                            scramble_buff
 * 1                            (filler) always 0x00
 * 2                            server_capabilities
 * 1                            server_language
 * 2                            server_status
 * 13                           (filler) always 0x00 ...
 * 13                           rest of scramble_buff (4.1)
 * 
 * @see http://forge.mysql.com/wiki/MySQL_Internals_ClientServer_Protocol#Handshake_Initialization_Packet
 * </pre>
 * 
 * @author mycat
 */
public class HandshakePacket extends MySQLPacket {
    private static final byte[] FILLER_13 = new byte[] { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };

    public byte protocolVersion;
    public byte[] serverVersion;
    public long threadId;
    public byte[] seed;
    public int serverCapabilities;
    public byte serverCharsetIndex;
    public int serverStatus;
    public byte[] restOfScrambleBuff;

    public void read(BinaryPacket bin) {
        packetLength = bin.packetLength;
        packetId = bin.packetId;
        MySQLMessage mm = new MySQLMessage(bin.data);
        protocolVersion = mm.read();
        serverVersion = mm.readBytesWithNull();
        threadId = mm.readUB4();
        seed = mm.readBytesWithNull();
        serverCapabilities = mm.readUB2();
        serverCharsetIndex = mm.read();
        serverStatus = mm.readUB2();
        mm.move(13);
        restOfScrambleBuff = mm.readBytesWithNull();
    }

    public void read(byte[] data) {
        MySQLMessage mm = new MySQLMessage(data);
        packetLength = mm.readUB3();
        packetId = mm.read();
        protocolVersion = mm.read();
        serverVersion = mm.readBytesWithNull();
        threadId = mm.readUB4();
        seed = mm.readBytesWithNull();
        serverCapabilities = mm.readUB2();
        serverCharsetIndex = mm.read();
        serverStatus = mm.readUB2();
        mm.move(13);
        restOfScrambleBuff = mm.readBytesWithNull();
    }


    // 握手报文：消息头 + 消息体。
    // 消息头：消息长度 + 消息序列号
    public void write(FrontendConnection c) {
        ByteBuffer buffer = c.allocate();

        // 3 字节，存放消息长度：81。不包括接下来的 packetID
        BufferUtil.writeUB3(buffer, calcPacketSize());

        // 1 字节的消息序号（packetId）
        buffer.put(packetId);

        // 1 + 36 + 4 + 9 + 2 + 1 + 1 + 13 + 13 = 81
        buffer.put(protocolVersion);                        // 1 Versions.PROTOCOL_VERSION = 10
        BufferUtil.writeWithNull(buffer, serverVersion);    //  36  =  "5.6.29-dadb-1.6-BETA-20160929210846".getBytes().length + 1(NULL)
        BufferUtil.writeUB4(buffer, threadId);              // 4
        BufferUtil.writeWithNull(buffer, seed);             // 9 = 8 + 1(NULL)
        BufferUtil.writeUB2(buffer, serverCapabilities);    // 2
        buffer.put(serverCharsetIndex);                     // 1
        BufferUtil.writeUB2(buffer, serverStatus);          // 2
        buffer.put(FILLER_13);                              // 13
        BufferUtil.writeWithNull(buffer, restOfScrambleBuff);       // 12 + 1
        c.write(buffer);
    }

    @Override
    public int calcPacketSize() {
        int size = 1;                               // 1    protocol version
        size += serverVersion.length;               // n           serverVersion
        size += 5;// 1+4                            // 1+4  1 是 serverVersion 写入后的 NULL， 4 是 thread_id
        size += seed.length;// 8                    // 8    scramble_buff
        size += 19;// 1+2+1+2+13                    // 1+2+1+2+13        filler + server_capabilities + server_language + server_status
        size += restOfScrambleBuff.length;// 12     // 12   scramble_buff
        size += 1;// 1
        return size;
    }

    @Override
    protected String getPacketInfo() {
        return "MySQL Handshake Packet";
    }

}