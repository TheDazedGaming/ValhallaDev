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

import net.sf.odinms.client.MapleCharacter;
import net.sf.odinms.client.MapleClient;
import net.sf.odinms.client.messages.Command;
import net.sf.odinms.client.messages.CommandDefinition;
import net.sf.odinms.client.messages.IllegalCommandSyntaxException;
import net.sf.odinms.client.messages.MessageCallback;

public class CharInfoCommand implements Command {
	@Override
	public void execute(MapleClient c, MessageCallback mc, String[] splittedLine) throws Exception,
																					IllegalCommandSyntaxException {
		MapleCharacter other = c.getChannelServer().getPlayerStorage().getCharacterByName(splittedLine[1]);
		mc.dropMessage(MapleClient.getLogMessage(other, "")
			+ " at X/Y: " + other.getPosition().x + "/" + other.getPosition().y
			+ " MapId: " + other.getMapId()
			+ " MapName: " + other.getMap().getMapName()
			+ " HP: " + other.getHp() + "/" + other.getCurrentMaxHp()
			+ " MP: " + other.getMp() + "/" + other.getCurrentMaxMp()
			+ " EXP: " + other.getExp()
			+ " Job: " + other.getJob().name()
			+ " GuildId: " + other.getGuildId()
			+ " EventStatus: " + (other.getEventInstance() != null)
			+ " PartyStatus: " + (other.getParty() != null)
			+ " TradeStatus: " + (other.getTrade() != null)
			+ " IP: " + other.getClient().getSession().getRemoteAddress().toString().substring(1, (':'))
			+ " Macs: " + other.getClient().getMacs());
	}

	@Override
	public CommandDefinition[] getDefinition() {
		return new CommandDefinition[] {
			new CommandDefinition("charinfo", "charname", "Shows info about the charcter with the given name", 1),
		};
	}
}
