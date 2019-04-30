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

import java.util.List;
import java.util.concurrent.ScheduledFuture;

import net.sf.odinms.client.ISkill;
import net.sf.odinms.client.MapleClient;
import net.sf.odinms.client.MapleCharacter.CancelCooldownAction;
import net.sf.odinms.client.SkillFactory;
import net.sf.odinms.net.MaplePacket;
import net.sf.odinms.server.MapleStatEffect;
import net.sf.odinms.server.TimerManager;
import net.sf.odinms.tools.MaplePacketCreator;
import net.sf.odinms.tools.Pair;
import net.sf.odinms.tools.data.input.SeekableLittleEndianAccessor;

public class MagicDamageHandler extends AbstractDealDamageHandler {

	@Override
	public void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {
	    AttackInfo attack = parseDamage(slea, false);

	    MaplePacket packet;
	    switch (attack.skill) {
		case 2121001:
		case 2221001:
		case 2321001:
		    packet = MaplePacketCreator.magicAttack(c.getPlayer().getId(), attack.skill, attack.stance, attack.numAttackedAndDamage, attack.allDamage, attack.charge, attack.speed);
		    break;
		default:
		    packet = MaplePacketCreator.magicAttack(c.getPlayer().getId(), attack.skill, attack.stance, attack.numAttackedAndDamage, attack.allDamage, -1, attack.speed);
		    break;
	    }

	    c.getPlayer().getMap().broadcastMessage(c.getPlayer(), packet, false, true);

	    int maxdamage;
	    maxdamage = 99999; // TODO fix magic damage calculation

	    ISkill skill = SkillFactory.getSkill(attack.skill);
	    MapleStatEffect effect_ = skill.getEffect(c.getPlayer().getSkillLevel(skill));

	    if (effect_.getCooldown() > 0) {
		if (c.getPlayer().skillisCooling(attack.skill)) {
		    return;
		}
		c.getSession().write(MaplePacketCreator.skillCooldown(attack.skill, effect_.getCooldown()));
		ScheduledFuture<?> timer = TimerManager.getInstance().schedule(new CancelCooldownAction(c.getPlayer(), attack.skill), effect_.getCooldown() * 1000);
		c.getPlayer().addCooldown(attack.skill, System.currentTimeMillis(), effect_.getCooldown() * 1000, timer);
	    }

	    MapleStatEffect effect = attack.getAttackEffect(c.getPlayer());
	    applyAttack(attack, c.getPlayer(), maxdamage, effect.getAttackCount());

	    for (int i = 1; i <= 3; i++) { // MP Eater
		ISkill eaterSkill = SkillFactory.getSkill(2000000 + i * 100000);
		int eaterLevel = c.getPlayer().getSkillLevel(eaterSkill);
		if (eaterLevel > 0) {
		    for (Pair<Integer, List<Integer>> singleDamage : attack.allDamage) {
			eaterSkill.getEffect(eaterLevel).applyPassive(c.getPlayer(), c.getPlayer().getMap().getMapObject(singleDamage.getLeft()), 0);
		    }
		    break;
		}
	    }
	}
}