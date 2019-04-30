/*
	This file is part of the OdinMS Maple Story Server
    Copyright (C) 2008 Patrick Huy <patrick.huy@frz.cc>
                       Matthias Butz <matze@odinms.de>
                       Jan Christian Meyer <vimes@odinms.de>

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License version 3
    as published by the Free Software Foundation. You may not use, modify
    or distribute this program under any other version of the
    GNU Affero General Public License.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package net.sf.odinms.net.channel.handler;

import java.rmi.RemoteException;

import net.sf.odinms.client.MapleCharacter;
import net.sf.odinms.client.MapleClient;
import net.sf.odinms.net.AbstractMaplePacketHandler;
import net.sf.odinms.net.channel.ChannelServer;
import net.sf.odinms.net.world.MapleMessenger;
import net.sf.odinms.net.world.MapleMessengerCharacter;
import net.sf.odinms.net.world.remote.WorldChannelInterface;
import net.sf.odinms.tools.MaplePacketCreator;
import net.sf.odinms.tools.data.input.SeekableLittleEndianAccessor;

public class MessengerHandler extends AbstractMaplePacketHandler {

	public void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {
	    String input;
	    byte mode = slea.readByte();
	    WorldChannelInterface wci = ChannelServer.getInstance(c.getChannel()).getWorldInterface();
	    MapleMessenger messenger = c.getPlayer().getMessenger();

	    switch (mode) {
		case 0x00: // open
		    if (messenger == null) {
			int messengerid = slea.readInt();
			if (messengerid == 0) { // create
			    try {
				MapleMessengerCharacter messengerplayer = new MapleMessengerCharacter(c.getPlayer());
				messenger = wci.createMessenger(messengerplayer);
				c.getPlayer().setMessenger(messenger);
				c.getPlayer().setMessengerPosition(0);
			    } catch (RemoteException e) {
				c.getChannelServer().reconnectWorld();
			    }
			} else { // join
			    try {
				messenger = wci.getMessenger(messengerid);
				int position = messenger.getLowestPosition();
				MapleMessengerCharacter messengerplayer = new MapleMessengerCharacter(c.getPlayer(), position);
				if (messenger != null) {
				    if (messenger.getMembers().size() < 3) {
					c.getPlayer().setMessenger(messenger);
					c.getPlayer().setMessengerPosition(position);
					wci.joinMessenger(messenger.getId(), messengerplayer, c.getPlayer().getName(), messengerplayer.getChannel());
				    }
				}
			    } catch (RemoteException e) {
				c.getChannelServer().reconnectWorld();
			    }
			}
		    }
		    break;
		case 0x02: // exit
		    if (messenger != null) {
			MapleMessengerCharacter messengerplayer = new MapleMessengerCharacter(c.getPlayer());
			try {
			    wci.leaveMessenger(messenger.getId(), messengerplayer);
			} catch (RemoteException e) {
			    c.getChannelServer().reconnectWorld();
			}
			c.getPlayer().setMessenger(null);
			c.getPlayer().setMessengerPosition(4);
		    }
		    break;
		case 0x03: // invite
		    if (messenger.getMembers().size() < 3) {
			input = slea.readMapleAsciiString();
			MapleCharacter target = c.getChannelServer().getPlayerStorage().getCharacterByName(input);
			if (target != null) {
			    if (target.getMessenger() == null) {
				target.getClient().getSession().write(MaplePacketCreator.messengerInvite(c.getPlayer().getName(), messenger.getId()));
				c.getSession().write(MaplePacketCreator.messengerNote(input, 4, 1));
			    } else {
				c.getSession().write(MaplePacketCreator.messengerChat(c.getPlayer().getName() + " : " + input + " is already using Maple Messenger"));
			    }
			} else {
			    try {
				if (ChannelServer.getInstance(c.getChannel()).getWorldInterface().isConnected(input)) {
				    ChannelServer.getInstance(c.getChannel()).getWorldInterface().messengerInvite(c.getPlayer().getName(), messenger.getId(), input, c.getChannel());
				} else {
				    c.getSession().write(MaplePacketCreator.messengerNote(input, 4, 0));
				}
			    } catch (RemoteException e) {
				c.getChannelServer().reconnectWorld();
			    }
			}
		    } else {
			c.getSession().write(MaplePacketCreator.messengerChat(c.getPlayer().getName() + " : You cannot have more than 3 people in the Maple Messenger"));
		    }
		    break;
		case 0x05: // decline
		    String targeted = slea.readMapleAsciiString();
		    MapleCharacter target = c.getChannelServer().getPlayerStorage().getCharacterByName(targeted);
		    if (target != null) {
			if (target.getMessenger() != null) {
			    target.getClient().getSession().write(MaplePacketCreator.messengerNote(c.getPlayer().getName(), 5, 0));
			}
		    } else {
			try {
			    wci.declineChat(targeted, c.getPlayer().getName());
			} catch (RemoteException e) {
			    c.getChannelServer().reconnectWorld();
			}
		    }
		    break;
		case 0x06: // message
		    if (messenger != null) {
			MapleMessengerCharacter messengerplayer = new MapleMessengerCharacter(c.getPlayer());
			input = slea.readMapleAsciiString();
			try {
			    wci.messengerChat(messenger.getId(), input, messengerplayer.getName());
			} catch (RemoteException e) {
			    c.getChannelServer().reconnectWorld();
			}
		    }
		    break;
		default:
		    c.getPlayer().getClient().disconnect();
		    break;
	    }
	}
}