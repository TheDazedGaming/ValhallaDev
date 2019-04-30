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

import net.sf.odinms.client.MapleClient;
import net.sf.odinms.client.messages.Command;
import net.sf.odinms.client.messages.CommandDefinition;
import net.sf.odinms.client.messages.MessageCallback;
import net.sf.odinms.tools.StringUtil;

public class ArrayCommand implements Command {
        
        @Override
        public void execute(MapleClient c, MessageCallback mc, String[] splittedLine) throws Exception {
                if (splittedLine.length >= 2)
                {
                  if (splittedLine[1].equalsIgnoreCase("*CLEAR"))
                  {
                    c.getChannelServer().setArrayString("");
                    mc.dropMessage("Array Sucessfully Flushed");
                  }
                  else
                  {
                    c.getChannelServer().setArrayString(c.getChannelServer().getArrayString() + StringUtil.joinStringFrom(splittedLine, 1));
                    mc.dropMessage("Added " + StringUtil.joinStringFrom(splittedLine, 1) + " to the array. Use !array to check.");
                  }
                }
                else
                  mc.dropMessage("Array: " + c.getChannelServer().getArrayString());
	}

	@Override
	public CommandDefinition[] getDefinition() {
		return new CommandDefinition[] {
			new CommandDefinition("array", "[Message to append]", "NOTE: When appending '*CLEAR' array will flush.", 100),
		};
	}

}
