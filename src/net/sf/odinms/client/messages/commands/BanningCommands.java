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

package net.sf.odinms.client.messages.commands;

import java.text.DateFormat;
import java.util.Calendar;

import net.sf.odinms.client.MapleCharacter;
import net.sf.odinms.client.MapleCharacterUtil;
import net.sf.odinms.client.MapleClient;
import net.sf.odinms.client.messages.Command;
import net.sf.odinms.client.messages.CommandDefinition;
import net.sf.odinms.client.messages.IllegalCommandSyntaxException;
import net.sf.odinms.client.messages.MessageCallback;
import net.sf.odinms.net.channel.ChannelServer;
import net.sf.odinms.tools.MaplePacketCreator;
import net.sf.odinms.tools.StringUtil;

public class BanningCommands implements Command {
    @Override
    public void execute(MapleClient c, MessageCallback mc, String[] splitted) throws Exception {
	ChannelServer cserv = c.getChannelServer();

	if (splitted[0].equals("!ban")) {
	    if (splitted.length < 3) {
		throw new IllegalCommandSyntaxException(3);
	    }
	    String originalReason = StringUtil.joinStringFrom(splitted, 2);
	    String reason = c.getPlayer().getName() + " banned " + splitted[1] + ": " + originalReason;
	    MapleCharacter target = cserv.getPlayerStorage().getCharacterByName(splitted[1]);

	    if (target != null) {
		String readableTargetName = MapleCharacterUtil.makeMapleReadable(target.getName());
		String ip = target.getClient().getSession().getRemoteAddress().toString().split(":")[0];
		reason += " (IP: " + ip + ")";
		target.ban(reason, true);
		cserv.broadcastPacket(MaplePacketCreator.serverNotice(6, readableTargetName + " has been banned for " + originalReason));
		mc.dropMessage(readableTargetName + "'s IP: " + ip + "!");
	    } else {
		if (MapleCharacter.ban(splitted[1], reason, false)) {
		    String readableTargetName = MapleCharacterUtil.makeMapleReadable(target.getName());
		    String ip = target.getClient().getSession().getRemoteAddress().toString().split(":")[0];
                    reason += " (IP: " + ip + ")";
                    cserv.broadcastPacket(MaplePacketCreator.serverNotice(6, readableTargetName + " has been banned for " + originalReason));
		} else {
		    mc.dropMessage("Failed to ban " + splitted[1]);
		}
	    }
	} else if (splitted[0].equals("!tempban")) {
	    MapleCharacter victim = cserv.getPlayerStorage().getCharacterByName(splitted[1]);
	    int reason = (Integer.parseInt(splitted[2]));
	    int numDay = (Integer.parseInt(splitted[3]));

	    Calendar cal = Calendar.getInstance();
	    cal.add(Calendar.DATE, numDay);
	    DateFormat df = DateFormat.getInstance();

	    if (victim == null) {
		mc.dropMessage("Unable to find character");
		return;
	    }

	    victim.tempban("Temp banned by : " + c.getPlayer().getName() + "", cal, reason, true);
	    mc.dropMessage("The character " + splitted[1] + " has been successfully tempbanned till " + df.format(cal.getTime()));

	} else if (splitted[0].equals("!dc")) {
	    int level = 0;
	    MapleCharacter victim;
	    if (splitted[1].charAt(0) == '-') {
		level = StringUtil.countCharacters(splitted[1], 'f');
				victim = cserv.getPlayerStorage().getCharacterByName(splitted[2]);
			} else {
				victim = cserv.getPlayerStorage().getCharacterByName(splitted[1]);
			}

                        if (level < 2)
                        {
                          victim.getClient().getSession().close();
                          if (level >= 1) {
                    victim.getClient().disconnect();
                }
			}
                        else {
                                mc.dropMessage("Please use dc -f instead.");
			}
		}
	}

	@Override
	public CommandDefinition[] getDefinition() {
		return new CommandDefinition[] {
			new CommandDefinition("ban", "charname reason", "Permanently ip, mac and accountbans the given character", 100),
			new CommandDefinition("tempban", "<name> <reason> <numDay>", "Tempbans the given account", 100),
			new CommandDefinition("dc", "[-f] name", "Disconnects player matching name provided. Use -f only if player is persistant!", 100),
		};
	}

}