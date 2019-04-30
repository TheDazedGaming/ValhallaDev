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

import static net.sf.odinms.client.messages.CommandProcessor.getOptionalIntArg;
import net.sf.odinms.client.MapleClient;
import net.sf.odinms.client.messages.Command;
import net.sf.odinms.client.messages.CommandDefinition;
import net.sf.odinms.client.messages.IllegalCommandSyntaxException;
import net.sf.odinms.client.messages.MessageCallback;
import net.sf.odinms.tools.MaplePacketCreator;
import net.sf.odinms.tools.StringUtil;

public class TestCommands implements Command {
	@Override
	public void execute(MapleClient c, MessageCallback mc, String[] splitted) throws Exception,
																				IllegalCommandSyntaxException {
		if (splitted[0].equals("!test")) {
			// faeks id is 30000 (30 75 00 00)
			// MapleCharacter faek = ((MapleCharacter) c.getPlayer().getMap().getMapObject(30000));

			c.getSession().write(MaplePacketCreator.getPacketFromHexString("2B 00 14 30 C0 23 00 00 11 00 00 00"));
		} else if (splitted[0].equals("!clock")) {
			c.getPlayer().getMap().broadcastMessage(MaplePacketCreator.getClock(getOptionalIntArg(splitted, 1, 60)));
		} else if (splitted[0].equals("!packet")) {
		   if (splitted.length > 1) {
			c.getSession().write(MaplePacketCreator.getPacketFromHexString(StringUtil.joinStringFrom(splitted, 0)));
		   } else {
                        mc.dropMessage("Please enter packet data!");
                   }
                } else if (splitted[0].equalsIgnoreCase("!invismonster")) {
                    c.getPlayer().getMap().broadcastMessage(MaplePacketCreator.makeMonsterInvisible(c.getPlayer().getMap().getMonsterByOid(Integer.parseInt(splitted[1]))));
                } else if (splitted[0].equalsIgnoreCase("!gimmecp")) {
                    if (splitted.length > 1) {
                        c.getPlayer().gainCP(Integer.parseInt(splitted[1]));
                    }
                } else if (splitted[0].equalsIgnoreCase("!playerdied")) {
                    if (splitted.length > 2) {
                        c.getPlayer().getMap().broadcastMessage(MaplePacketCreator.playerDiedMessage(splitted[1], Integer.parseInt(splitted[2]), c.getPlayer().getTeam()));
                    }
                } else if (splitted[0].equalsIgnoreCase("!playersummoned")) {
                    if (splitted.length > 3) {
                        c.getPlayer().getMap().broadcastMessage(MaplePacketCreator.playerSummoned(splitted[1], Integer.parseInt(splitted[2]), Integer.parseInt(splitted[3])));
                    }
                } else if (splitted[0].equalsIgnoreCase("!curpos")) {
                    mc.dropMessage("Current position: " + c.getPlayer().getPosition().getX() + ", " + c.getPlayer().getPosition().getY());
                } else if (splitted[0].equalsIgnoreCase("curteam")){
                    mc.dropMessage("Team: " + c.getPlayer().getTeam());
                }

	}

	@Override
	public CommandDefinition[] getDefinition() {
		return new CommandDefinition[] {
			new CommandDefinition("test", "?", "Probably does something", 100),
			new CommandDefinition("clock", "[time]", "Shows a clock to everyone in the map", 100),
			new CommandDefinition("packet", "hex data", "Shows a clock to everyone in the map", 100),
                        new CommandDefinition("invismonster", "", "", 100),
                        new CommandDefinition("gimmecp", "", "", 100),
                        new CommandDefinition("playerdied", "", "", 100),
                        new CommandDefinition("playersummoned", "", "", 100),
                        new CommandDefinition("curpos", "", "", 100),
                        new CommandDefinition("curteam", "", "", 100),
		};
	}

}
