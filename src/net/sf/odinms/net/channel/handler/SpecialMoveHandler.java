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

import java.awt.Point;
import java.util.concurrent.ScheduledFuture;

import net.sf.odinms.client.ISkill;
import net.sf.odinms.client.MapleCharacter.CancelCooldownAction;
import net.sf.odinms.client.MapleClient;
import net.sf.odinms.client.SkillFactory;
import net.sf.odinms.net.AbstractMaplePacketHandler;
import net.sf.odinms.server.AutobanManager;
import net.sf.odinms.server.MapleStatEffect;
import net.sf.odinms.server.TimerManager;
import net.sf.odinms.server.life.MapleMonster;
import net.sf.odinms.tools.MaplePacketCreator;
import net.sf.odinms.tools.data.input.SeekableLittleEndianAccessor;

public class SpecialMoveHandler extends AbstractMaplePacketHandler {

//    private static org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SpecialMoveHandler.class);

	@Override
	public void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {
	    // [53 00] [12 62] [AA 01] [6B 6A 23 00] [1E] [BA 00] [97 00] 00
	    slea.readShort(); // oldX
	    slea.readShort(); //oldY
	    int skillid = slea.readInt();
	    int skillLevel = slea.readByte();

	    Point pos = null;
	    ISkill skill = SkillFactory.getSkill(skillid);

	    MapleStatEffect effect = skill.getEffect(c.getPlayer().getSkillLevel(skill));

	    if (!c.getPlayer().isAlive()) {
		c.getSession().write(MaplePacketCreator.enableActions());
        return;
	    }

	    if (c.getPlayer().getSkillLevel(skill) == 0 || c.getPlayer().getSkillLevel(skill) != skillLevel) {
		AutobanManager.getInstance().addPoints(c.getPlayer().getClient(), 1000, 0, "Using a move skill he doesn't have (" + skill.getId() + ")");
		return;
	    }

	    if (effect.getCooldown() > 0) {
		if (c.getPlayer().skillisCooling(skillid)) {
		    return;
		}
		c.getSession().write(MaplePacketCreator.skillCooldown(skillid, effect.getCooldown()));
		ScheduledFuture<?> timer = TimerManager.getInstance().schedule(new CancelCooldownAction(c.getPlayer(), skillid), effect.getCooldown() * 1000);
		c.getPlayer().addCooldown(skillid, System.currentTimeMillis(), effect.getCooldown() * 1000, timer);
	    }

	    if (skillid == 1121001 || skillid == 1221001 || skillid == 1321001) { // Monster Magnet and Grenade
		int num = slea.readInt();
		int mobId;
		byte success;

		for (int i = 0; i < num; i++) {
		    mobId = slea.readInt();
		    success = slea.readByte();
		    c.getPlayer().getMap().broadcastMessage(c.getPlayer(), MaplePacketCreator.showMagnet(mobId, success), false);
		    MapleMonster monster = c.getPlayer().getMap().getMonsterByOid(mobId);
		    if (monster != null) {
			monster.switchController(c.getPlayer(), monster.isControllerHasAggro());
		    }
		}
		byte direction = slea.readByte();
		c.getPlayer().getMap().broadcastMessage(c.getPlayer(), MaplePacketCreator.showBuffeffect(c.getPlayer().getId(), skillid, 1, direction), false);
		c.getSession().write(MaplePacketCreator.enableActions());
		return;
	    }

	    if (slea.available() == 5) {
		pos = new Point(slea.readShort(), slea.readShort());
	    }

	    if (skill.getId() != 2311002 || c.getPlayer().canDoor()) {
		skill.getEffect(c.getPlayer().getSkillLevel(skill)).applyTo(c.getPlayer(), pos);
	    } else {
		c.getSession().write(MaplePacketCreator.serverNotice(5, "Please wait 5 seconds before casting Mystic Door again."));
		c.getSession().write(MaplePacketCreator.enableActions());
	    }
	}
}