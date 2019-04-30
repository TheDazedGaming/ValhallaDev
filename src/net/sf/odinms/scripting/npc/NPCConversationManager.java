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

package net.sf.odinms.scripting.npc;

import java.util.Arrays;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;

import net.sf.odinms.client.IItem;
import net.sf.odinms.client.Item;
import net.sf.odinms.client.MapleCharacter;
import net.sf.odinms.client.MapleClient;
import net.sf.odinms.client.MapleInventory;
import net.sf.odinms.client.MapleInventoryType;
import net.sf.odinms.client.MapleJob;
import net.sf.odinms.client.SkillFactory;
import net.sf.odinms.scripting.AbstractPlayerInteraction;
import net.sf.odinms.scripting.event.EventManager;
import net.sf.odinms.server.MapleInventoryManipulator;
import net.sf.odinms.server.MapleItemInformationProvider;
import net.sf.odinms.server.MapleShopFactory;
import net.sf.odinms.server.quest.MapleQuest;
import net.sf.odinms.tools.MaplePacketCreator;
import net.sf.odinms.client.MapleStat;
import net.sf.odinms.net.world.guild.MapleGuild;
import net.sf.odinms.server.MapleSquad;
import net.sf.odinms.server.MapleSquadType;
import net.sf.odinms.server.maps.MapleMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import net.sf.odinms.client.ISkill;
import net.sf.odinms.net.channel.ChannelServer;
import net.sf.odinms.net.world.MaplePartyCharacter;
import net.sf.odinms.server.MaplePortal;
import net.sf.odinms.server.MapleStatEffect;
import net.sf.odinms.server.maps.MapleMapFactory;
import net.sf.odinms.server.maps.MapleMapObject;
import net.sf.odinms.server.maps.MapleMapObjectType;

/**
 *
 * @author Matze
 */
public class NPCConversationManager extends AbstractPlayerInteraction {

	private MapleClient c;
	private int npc;
	private String getText;
        private MapleCharacter chr;

	public NPCConversationManager(MapleClient c, int npc) {
		super(c);
		this.c = c;
		this.npc = npc;
	}
        
        public NPCConversationManager(MapleClient c, int npc, MapleCharacter chr) {
            super(c);
            this.c = c;
            this.npc = npc;
            this.chr = chr;
        }

	public void dispose() {
		NPCScriptManager.getInstance().dispose(this);
	}

	public void sendNext(String text) {
		getClient().getSession().write(MaplePacketCreator.getNPCTalk(npc, (byte) 0, text, "00 01"));
	}

	public void sendPrev(String text) {
		getClient().getSession().write(MaplePacketCreator.getNPCTalk(npc, (byte) 0, text, "01 00"));
	}

	public void sendNextPrev(String text) {
		getClient().getSession().write(MaplePacketCreator.getNPCTalk(npc, (byte) 0, text, "01 01"));
	}

	public void sendOk(String text) {
		getClient().getSession().write(MaplePacketCreator.getNPCTalk(npc, (byte) 0, text, "00 00"));
	}

	public void sendYesNo(String text) {
		getClient().getSession().write(MaplePacketCreator.getNPCTalk(npc, (byte) 1, text, ""));
	}

	public void sendAcceptDecline(String text) {
		getClient().getSession().write(MaplePacketCreator.getNPCTalk(npc, (byte) 0x0C, text, ""));
	}

	public void sendSimple(String text) {
		getClient().getSession().write(MaplePacketCreator.getNPCTalk(npc, (byte) 4, text, ""));
	}

	public void sendStyle(String text, int styles[]) {
		getClient().getSession().write(MaplePacketCreator.getNPCTalkStyle(npc, text, styles));
	}

	public void sendGetNumber(String text, int def, int min, int max) {
		getClient().getSession().write(MaplePacketCreator.getNPCTalkNum(npc, text, def, min, max));
	}

	public void sendGetText(String text) {
		getClient().getSession().write(MaplePacketCreator.getNPCTalkText(npc, text));
	}

	public void setGetText(String text) {
		this.getText = text;
	}

	public String getText() {
		return this.getText;
	}

	public void openShop(int id) {
		MapleShopFactory.getInstance().getShop(id).sendShop(getClient());
	}

	public void openNpc(int id) {
		dispose();
		NPCScriptManager.getInstance().start(getClient(), id, null, null);
	}

	public void changeJob(MapleJob job) {
		getPlayer().changeJob(job);
	}

	public MapleJob getJob() {
		return getPlayer().getJob();
	}

	public void startQuest(int id) {
		MapleQuest.getInstance(id).start(getPlayer(), npc);
	}

	public void completeQuest(int id) {
		MapleQuest.getInstance(id).complete(getPlayer(), npc);
	}

	public void forfeitQuest(int id) {
		MapleQuest.getInstance(id).forfeit(getPlayer());
	}

	public void gainMeso(int gain) {
		getPlayer().gainMeso(gain, true, false, true);
	}

	public void gainExp(int gain) {
		getPlayer().gainExp(gain, true, true);
	}

	public int getNpc() {
		return npc;
	}

	/**
	 * use getPlayer().getLevel() instead
	 * @return
	 */
	@Deprecated
	public int getLevel() {
		return getPlayer().getLevel();
	}

	public void unequipEverything() {
		MapleInventory equipped = getPlayer().getInventory(MapleInventoryType.EQUIPPED);
		MapleInventory equip = getPlayer().getInventory(MapleInventoryType.EQUIP);
		List<Byte> ids = new LinkedList<Byte>();
		for (IItem item : equipped.list()) {
			ids.add(item.getPosition());
		}
		for (byte id : ids) {
			MapleInventoryManipulator.unequip(getC(), id, equip.getNextFreeSlot());
		}
	}

	public void teachSkill(int id, int level, int masterlevel) {
		getPlayer().changeSkillLevel(SkillFactory.getSkill(id), level, masterlevel);
	}

	public void clearSkills() {
		Map<ISkill, MapleCharacter.SkillEntry> skills = getPlayer().getSkills();
		for (Entry<ISkill, MapleCharacter.SkillEntry> skill : skills.entrySet()) {
			getPlayer().changeSkillLevel(skill.getKey(), 0, 0);
		}
	}

	public MapleClient getC() {
		return getClient();
	}

    @Deprecated
    public MapleCharacter getChar() {
        return getPlayer();
    }

	public void rechargeStars() {
		MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
		IItem stars = getPlayer().getInventory(MapleInventoryType.USE).getItem((byte) 1);
		if (ii.isThrowingStar(stars.getItemId()) || ii.isBullet(stars.getItemId())) {
			stars.setQuantity(ii.getSlotMax(getClient(), stars.getItemId()));
			getC().getSession().write(MaplePacketCreator.updateInventorySlot(MapleInventoryType.USE, (Item) stars));
		}
	}

	public EventManager getEventManager(String event) {
		return getClient().getChannelServer().getEventSM().getEventManager(event);
	}

	public void showEffect(String effect) {
		getPlayer().getMap().broadcastMessage(MaplePacketCreator.showEffect(effect));
	}

	public void playSound(String sound) {
		getClient().getPlayer().getMap().broadcastMessage(MaplePacketCreator.playSound(sound));
	}

	@Override
	public String toString() {
		return "Conversation with NPC: " + npc;
	}

	public void updateBuddyCapacity(int capacity) {
		getPlayer().setBuddyCapacity(capacity);
	}

	public int getBuddyCapacity() {
		return getPlayer().getBuddyCapacity();
	}

	public void setHair(int hair) {
		getPlayer().setHair(hair);
		getPlayer().updateSingleStat(MapleStat.HAIR, hair);
		getPlayer().equipChanged();
	}

	public void setFace(int face) {
		getPlayer().setFace(face);
		getPlayer().updateSingleStat(MapleStat.FACE, face);
		getPlayer().equipChanged();
	}

	@SuppressWarnings("static-access")
	public void setSkin(int color) {
		getPlayer().setSkinColor(getPlayer().getSkinColor().getById(color));
		getPlayer().updateSingleStat(MapleStat.SKIN, color);
		getPlayer().equipChanged();
	}

	public void warpParty(int mapId) {
		MapleMap target = getMap(mapId);
		for (MaplePartyCharacter chr : getPlayer().getParty().getMembers()) {
			MapleCharacter curChar = c.getChannelServer().getPlayerStorage().getCharacterByName(chr.getName());
			if ((curChar.getEventInstance() == null && getPlayer().getEventInstance() == null) || curChar.getEventInstance() == getPlayer().getEventInstance()) {
				curChar.changeMap(target, target.getPortal(0));
			}
		}
	}

	public void warpPartyWithExp(int mapId, int exp) {
		MapleMap target = getMap(mapId);
		for (MaplePartyCharacter chr : getPlayer().getParty().getMembers()) {
			MapleCharacter curChar = c.getChannelServer().getPlayerStorage().getCharacterByName(chr.getName());
			if ((curChar.getEventInstance() == null && c.getPlayer().getEventInstance() == null) || curChar.getEventInstance() == getPlayer().getEventInstance()) {
				curChar.changeMap(target, target.getPortal(0));
				curChar.gainExp(exp, true, false, true);
			}
		}
	}

	public void warpPartyWithExpMeso(int mapId, int exp, int meso) {
		MapleMap target = getMap(mapId);
		for (MaplePartyCharacter chr : getPlayer().getParty().getMembers()) {
			MapleCharacter curChar = c.getChannelServer().getPlayerStorage().getCharacterByName(chr.getName());
			if ((curChar.getEventInstance() == null && c.getPlayer().getEventInstance() == null) || curChar.getEventInstance() == getPlayer().getEventInstance()) {
				curChar.changeMap(target, target.getPortal(0));
				curChar.gainExp(exp, true, false, true);
				curChar.gainMeso(meso, true);
			}
		}
	}

	public void warpRandom(int mapid) {
		MapleMap target = c.getChannelServer().getMapFactory().getMap(mapid);
		Random rand = new Random();
		MaplePortal portal = target.getPortal(rand.nextInt(target.getPortals().size())); //generate random portal
		getPlayer().changeMap(target, portal);
	}

	public int itemQuantity(int itemid) {
		MapleInventoryType type = MapleItemInformationProvider.getInstance().getInventoryType(itemid);
		MapleInventory iv = getPlayer().getInventory(type);
		int possesed = iv.countById(itemid);
		return possesed;
	}

	public MapleSquad createMapleSquad(MapleSquadType type) {
		MapleSquad squad = new MapleSquad(c.getChannel(), getPlayer());
		if (getSquadState(type) == 0) {
			c.getChannelServer().addMapleSquad(squad, type);
		} else {
			return null;
		}
		return squad;
	}

	public MapleCharacter getSquadMember(MapleSquadType type, int index) {
		MapleSquad squad = c.getChannelServer().getMapleSquad(type);
		MapleCharacter ret = null;
		if (squad != null) {
			ret = squad.getMembers().get(index);
		}
		return ret;
	}

	public int getSquadState(MapleSquadType type) {
		MapleSquad squad = c.getChannelServer().getMapleSquad(type);
		if (squad != null) {
			return squad.getStatus();
		} else {
			return 0;
		}
	}

	public void setSquadState(MapleSquadType type, int state) {
		MapleSquad squad = c.getChannelServer().getMapleSquad(type);
		if (squad != null) {
			squad.setStatus(state);
		}
	}

	public boolean checkSquadLeader(MapleSquadType type) {
		MapleSquad squad = c.getChannelServer().getMapleSquad(type);
		if (squad != null) {
			if (squad.getLeader().getId() == getPlayer().getId()) {
				return true;
			} else {
				return false;
			}
		} else {
			return false;
		}
	}

	public void removeMapleSquad(MapleSquadType type) {
		MapleSquad squad = c.getChannelServer().getMapleSquad(type);
		if (squad != null) {
			if (squad.getLeader().getId() == getPlayer().getId()) {
				squad.clear();
				c.getChannelServer().removeMapleSquad(squad, type);
			}
		}
	}

	public int numSquadMembers(MapleSquadType type) {
		MapleSquad squad = c.getChannelServer().getMapleSquad(type);
		int ret = 0;
		if (squad != null) {
			ret = squad.getSquadSize();
		}
		return ret;
	}

	public boolean isSquadMember(MapleSquadType type) {
		MapleSquad squad = c.getChannelServer().getMapleSquad(type);
		boolean ret = false;
		if (squad.containsMember(getPlayer())) {
			ret = true;
		}
		return ret;
	}

	public void addSquadMember(MapleSquadType type) {
		MapleSquad squad = c.getChannelServer().getMapleSquad(type);
		if (squad != null) {
			squad.addMember(getPlayer());
		}
	}

	public void removeSquadMember(MapleSquadType type, MapleCharacter chr, boolean ban) {
		MapleSquad squad = c.getChannelServer().getMapleSquad(type);
		if (squad != null) {
			squad.banMember(chr, ban);
		}
	}

	public void removeSquadMember(MapleSquadType type, int index, boolean ban) {
		MapleSquad squad = c.getChannelServer().getMapleSquad(type);
		if (squad != null) {
			MapleCharacter chr = squad.getMembers().get(index);
			squad.banMember(chr, ban);
		}
	}

	public boolean canAddSquadMember(MapleSquadType type) {
		MapleSquad squad = c.getChannelServer().getMapleSquad(type);
		if (squad != null) {
			if (squad.isBanned(getPlayer())) {
				return false;
			} else {
				return true;
			}
		}
		return false;
	}

	public void warpSquadMembers(MapleSquadType type, int mapId) {
		MapleSquad squad = c.getChannelServer().getMapleSquad(type);
		MapleMap map = c.getChannelServer().getMapFactory().getMap(mapId);
		if (squad != null) {
			if (checkSquadLeader(type)) {
				for (MapleCharacter chr : squad.getMembers()) {
					chr.changeMap(map, map.getPortal(0));
				}
			}
		}
	}

	public static boolean makeRing(MapleClient mc, String partner, int ringId) {
		int partnerId = MapleCharacter.getIdByName(partner, 0);
		int[] ret = net.sf.odinms.client.MapleRing.createRing(mc, ringId, mc.getPlayer().getId(), mc.getPlayer().getName(), partnerId, partner);
		if (ret[0] == -1 || ret[1] == -1) {
			return false;
		} else {
			return true;
		}
	}

	public void resetReactors() {
		getPlayer().getMap().resetReactors();
	}

	public void displayGuildRanks() {
		MapleGuild.displayGuildRanks(getClient(), npc);
	}

	public void openDuey() {
		c.getSession().write(MaplePacketCreator.sendDueyAction((byte) 8));
	}
        
        public MapleCharacter getCharacter() {
            return chr;
        }
        
       public MapleCharacter getCharByName(String namee) {
           try {
            return getClient().getChannelServer().getPlayerStorage().getCharacterByName(namee);
           } catch (Exception e) {
               return null;
           }
       }
       
        public void warpAllInMap(int mapid, int portal) {
                MapleMap outMap;
                MapleMapFactory mapFactory;
                mapFactory = ChannelServer.getInstance(c.getChannel()).getMapFactory();
                outMap = mapFactory.getMap(mapid);
                for (MapleCharacter aaa : outMap.getCharacters()) {
                    //Warp everyone out
                    mapFactory = ChannelServer.getInstance(aaa.getClient().getChannel()).getMapFactory();
                    aaa.getClient().getPlayer().changeMap(outMap, outMap.getPortal(portal));
                    outMap = mapFactory.getMap(mapid);
                    aaa.getClient().getPlayer().getEventInstance().unregisterPlayer(aaa.getClient().getPlayer()); //Unregister them all
                }
        }

       public int countMonster() {
            MapleMap map = c.getPlayer().getMap();
            double range = Double.POSITIVE_INFINITY;
            List<MapleMapObject> monsters = map.getMapObjectsInRange(c.getPlayer().getPosition(), range, Arrays
                    .asList(MapleMapObjectType.MONSTER));
            return monsters.size();
        }

        public int countReactor() {
            MapleMap map = c.getPlayer().getMap();
            double range = Double.POSITIVE_INFINITY;
            List<MapleMapObject> reactors = map.getMapObjectsInRange(c.getPlayer().getPosition(), range, Arrays
                    .asList(MapleMapObjectType.REACTOR));
            return reactors.size();
        }
        
        public int getDayOfWeek() {
            Calendar cal = Calendar.getInstance();
            int dayy = cal.get(Calendar.DAY_OF_WEEK);
            return dayy;
        }
        
        public void giveNPCBuff(MapleCharacter chr, int itemID) {
            MapleItemInformationProvider mii = MapleItemInformationProvider.getInstance();
            MapleStatEffect statEffect = mii.getItemEffect(itemID);
            statEffect.applyTo(chr);
        }
        
        public void giveWonkyBuff(MapleCharacter chr){
            long what = Math.round(Math.random() * 4);
            int what1 = (int)what;
            int Buffs[] = {2022090, 2022091, 2022092, 2022093} ;
            int buffToGive = Buffs[what1];
            MapleItemInformationProvider mii = MapleItemInformationProvider.getInstance();
            MapleStatEffect statEffect = mii.getItemEffect(buffToGive);
            //for (MapleMapObject mmo =  this.getParty()) {
            MapleCharacter character = (MapleCharacter) chr;
            statEffect.applyTo(character);
            //}
        }
}
