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

package net.sf.odinms.client;

import java.awt.Point;
import java.lang.ref.WeakReference;
import java.rmi.RemoteException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.text.NumberFormat;
import java.text.DecimalFormat;

import net.sf.odinms.client.anticheat.CheatTracker;
import net.sf.odinms.client.messages.MessageCallback;
import net.sf.odinms.database.DatabaseConnection;
import net.sf.odinms.database.DatabaseException;
import net.sf.odinms.net.MaplePacket;
import net.sf.odinms.net.PacketProcessor;
import net.sf.odinms.net.channel.ChannelServer;
import net.sf.odinms.net.world.MapleMessenger;
import net.sf.odinms.net.world.MapleMessengerCharacter;
import net.sf.odinms.net.world.MapleParty;
import net.sf.odinms.net.world.MaplePartyCharacter;
import net.sf.odinms.net.world.PartyOperation;
import net.sf.odinms.net.world.PlayerBuffValueHolder;
import net.sf.odinms.net.world.PlayerCoolDownValueHolder;
import net.sf.odinms.net.world.remote.WorldChannelInterface;
import net.sf.odinms.scripting.event.EventInstanceManager;
import net.sf.odinms.server.MapleItemInformationProvider;
import net.sf.odinms.server.MaplePlayerShop;
import net.sf.odinms.server.MaplePortal;
import net.sf.odinms.server.MapleShop;
import net.sf.odinms.server.MapleStatEffect;
import net.sf.odinms.server.MapleStorage;
import net.sf.odinms.server.MapleTrade;
import net.sf.odinms.server.TimerManager;
import net.sf.odinms.server.life.MapleMonster;
import net.sf.odinms.server.maps.AbstractAnimatedMapleMapObject;
import net.sf.odinms.server.maps.MapleDoor;
import net.sf.odinms.server.maps.MapleMap;
import net.sf.odinms.server.maps.MapleMapFactory;
import net.sf.odinms.server.maps.MapleMapObject;
import net.sf.odinms.server.maps.MapleMapObjectType;
import net.sf.odinms.server.maps.MapleSummon;
import net.sf.odinms.server.maps.SavedLocationType;
import net.sf.odinms.server.maps.SummonMovementType;
import net.sf.odinms.server.quest.MapleCustomQuest;
import net.sf.odinms.server.quest.MapleQuest;
import net.sf.odinms.tools.MaplePacketCreator;
import net.sf.odinms.tools.Pair;
import net.sf.odinms.net.world.guild.*;
import net.sf.odinms.server.MapleInventoryManipulator;
import net.sf.odinms.server.life.MobSkill;

public class MapleCharacter extends AbstractAnimatedMapleMapObject implements InventoryContainer {

	private static org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PacketProcessor.class);
	public static final double MAX_VIEW_RANGE_SQ = 850 * 850;
	private int world;
	private int accountid;
	private int rank;
	private int rankMove;
	private int jobRank;
	private int jobRankMove;
	private String name;
	private int level;
	private int str,  dex,  luk,  int_;
	private AtomicInteger exp = new AtomicInteger();
	private int hp,  maxhp;
	private int mp,  maxmp;
	private int mpApUsed,  hpApUsed;
	private int hair,  face;
	private AtomicInteger meso = new AtomicInteger();
	private int remainingAp,  remainingSp;
	private int savedLocations[];
	private int fame;
	private long lastfametime;
	private List<Integer> lastmonthfameids;

	// local stats represent current stats of the player to avoid expensive operations
	private transient int localmaxhp,  localmaxmp;
	private transient int localstr,  localdex,  localluk,  localint_;
	private transient int magic,  watk;
	private transient double speedMod,  jumpMod;
	private transient int localmaxbasedamage;
	private int id;
	private MapleClient client;
	private MapleMap map;
	private int initialSpawnPoint;

	// mapid is only used when calling getMapId() with map == null, it is not updated when running in channelserver mode
	private int mapid;
	private MapleShop shop = null;
	private MaplePlayerShop playerShop = null;
	private MapleStorage storage = null;
	private MaplePet[] pets = new MaplePet[3];
	private SkillMacro[] skillMacros = new SkillMacro[5];
	private MapleTrade trade = null;
	private MapleSkinColor skinColor = MapleSkinColor.NORMAL;
	private MapleJob job = MapleJob.BEGINNER;
	private int gender;
	private int gmLevel;
	private boolean hidden;
	private boolean canDoor = true;
	private int chair;
	private int itemEffect;
	private int APQScore;
	private MapleParty party;
	private EventInstanceManager eventInstance = null;
	private MapleInventory[] inventory;
	private Map<MapleQuest, MapleQuestStatus> quests;
	private Set<MapleMonster> controlled = new LinkedHashSet<MapleMonster>();
	private Set<MapleMapObject> visibleMapObjects = new LinkedHashSet<MapleMapObject>();
	private Map<ISkill, SkillEntry> skills = new LinkedHashMap<ISkill, SkillEntry>();
	private Map<MapleBuffStat, MapleBuffStatValueHolder> effects = new LinkedHashMap<MapleBuffStat, MapleBuffStatValueHolder>();
	private Map<Integer, MapleKeyBinding> keymap = new LinkedHashMap<Integer, MapleKeyBinding>();
	private List<MapleDoor> doors = new ArrayList<MapleDoor>();
	private Map<Integer, MapleSummon> summons = new LinkedHashMap<Integer, MapleSummon>();
	private BuddyList buddylist;
	private Map<Integer, MapleCoolDownValueHolder> coolDowns = new LinkedHashMap<Integer, MapleCoolDownValueHolder>();

	// anticheat related information
	private CheatTracker anticheat;
	private ScheduledFuture<?> dragonBloodSchedule;
	private ScheduledFuture<?> mapTimeLimitTask = null;

	//guild related information
	private int guildid;
	private int guildrank;
	private MapleGuildCharacter mgc = null;

	// cash shop related information
	private int paypalnx;
	private int maplepoints;
	private int cardnx;

	// misc information
	private List<MapleDisease> diseases = new ArrayList<MapleDisease>();
	private boolean incs;
    private boolean inmts;
	private MapleMessenger messenger = null;
	int messengerposition = 4;
	private int slots = 0;
	private ScheduledFuture<?> fullnessSchedule;
	private ScheduledFuture<?> fullnessSchedule_1;
	private ScheduledFuture<?> fullnessSchedule_2;
	private ScheduledFuture<?> hpDecreaseTask;
	private ScheduledFuture<?> beholderHealingSchedule;
	private ScheduledFuture<?> beholderBuffSchedule;
	private ScheduledFuture<?> BerserkSchedule;
	private boolean Berserk = false;
	public SummonMovementType getMovementType;
	private String chalktext; // Chalkboard
	private int CP;
	private int totalCP;
	private int team;
    private int canTalk;
    private int zakumLvl; //zero means they havent started yet

    //marriage
    private int married;
    private int partnerid;
    private int marriageQuestLevel;

    private int currentPage, currentType = 0;
    private int currentTab = 1;

    private MessageCallback mc;

	private MapleCharacter() {
		setStance(0);
		inventory = new MapleInventory[MapleInventoryType.values().length];
		for (MapleInventoryType type : MapleInventoryType.values()) {
			inventory[type.ordinal()] = new MapleInventory(type, (byte) 100);
		}

		savedLocations = new int[SavedLocationType.values().length];
		for (int i = 0; i < SavedLocationType.values().length; i++) {
			savedLocations[i] = -1;
		}

		quests = new LinkedHashMap<MapleQuest, MapleQuestStatus>();
		anticheat = new CheatTracker(this);
		setPosition(new Point(0, 0));
	}

    public void dropMessage(String string) {
                mc.dropMessage(string);
    }

	public MapleCharacter getThis() {
		return this;
	}

	public static MapleCharacter loadCharFromDB(int charid, MapleClient client, boolean channelserver) throws SQLException {
		MapleCharacter ret = new MapleCharacter();
		ret.client = client;
		ret.id = charid;

		Connection con = DatabaseConnection.getConnection();
		PreparedStatement ps = con.prepareStatement("SELECT * FROM characters WHERE id = ?");
		ps.setInt(1, charid);
		ResultSet rs = ps.executeQuery();
		if (!rs.next()) {
			throw new RuntimeException("Loading the Char Failed (char not found)");
		}
		ret.name = rs.getString("name");
		ret.level = rs.getInt("level");
		ret.fame = rs.getInt("fame");
		ret.str = rs.getInt("str");
		ret.dex = rs.getInt("dex");
		ret.int_ = rs.getInt("int");
		ret.luk = rs.getInt("luk");
		ret.exp.set(rs.getInt("exp"));

		ret.hp = rs.getInt("hp");
		ret.maxhp = rs.getInt("maxhp");
		ret.mp = rs.getInt("mp");
		ret.maxmp = rs.getInt("maxmp");

		ret.hpApUsed = rs.getInt("hpApUsed");
		ret.mpApUsed = rs.getInt("mpApUsed");
		ret.remainingSp = rs.getInt("sp");
		ret.remainingAp = rs.getInt("ap");

		ret.meso.set(rs.getInt("meso"));

		ret.gmLevel = rs.getInt("gm");

		ret.skinColor = MapleSkinColor.getById(rs.getInt("skincolor"));
		ret.gender = rs.getInt("gender");
		ret.job = MapleJob.getById(rs.getInt("job"));
                //cantalk
                ret.canTalk = rs.getInt("cantalk");
                //marriage
                ret.married = rs.getInt("married");
                ret.partnerid = rs.getInt("partnerid");
                ret.marriageQuestLevel = rs.getInt("marriagequest");

                ret.zakumLvl = rs.getInt("zakumLvl");

		ret.hair = rs.getInt("hair");
		ret.face = rs.getInt("face");

		ret.accountid = rs.getInt("accountid");

		ret.mapid = rs.getInt("map");
		ret.initialSpawnPoint = rs.getInt("spawnpoint");
		ret.world = rs.getInt("world");

		ret.rank = rs.getInt("rank");
		ret.rankMove = rs.getInt("rankMove");
		ret.jobRank = rs.getInt("jobRank");
		ret.jobRankMove = rs.getInt("jobRankMove");

		ret.guildid = rs.getInt("guildid");
		ret.guildrank = rs.getInt("guildrank");
		if (ret.guildid > 0) {
			ret.mgc = new MapleGuildCharacter(ret);
		}

		int buddyCapacity = rs.getInt("buddyCapacity");
		ret.buddylist = new BuddyList(buddyCapacity);

		if (channelserver) {
			MapleMapFactory mapFactory = ChannelServer.getInstance(client.getChannel()).getMapFactory();
			ret.map = mapFactory.getMap(ret.mapid);
			if (ret.map == null) { //char is on a map that doesn't exist warp it to henesys
				ret.map = mapFactory.getMap(100000000);
			}
                        int rMap = ret.map.getForcedReturnId();
                        if (rMap != 999999999) {
                                ret.map = mapFactory.getMap(rMap);
                        }
			MaplePortal portal = ret.map.getPortal(ret.initialSpawnPoint);
			if (portal == null) {
				portal = ret.map.getPortal(0); // char is on a spawnpoint that doesn't exist - select the first spawnpoint instead
				ret.initialSpawnPoint = 0;
			}
			ret.setPosition(portal.getPosition());

			int partyid = rs.getInt("party");
			if (partyid >= 0) {
				try {
					MapleParty party = client.getChannelServer().getWorldInterface().getParty(partyid);
					if (party != null && party.getMemberById(ret.id) != null) {
						ret.party = party;
					}
				} catch (RemoteException e) {
					client.getChannelServer().reconnectWorld();
				}
			}

			int messengerid = rs.getInt("messengerid");
			int position = rs.getInt("messengerposition");
			if (messengerid > 0 && position < 4 && position > -1) {
				try {
					WorldChannelInterface wci = ChannelServer.getInstance(client.getChannel()).getWorldInterface();
					MapleMessenger messenger = wci.getMessenger(messengerid);
					if (messenger != null) {
						ret.messenger = messenger;
						ret.messengerposition = position;
					}
				} catch (RemoteException e) {
					client.getChannelServer().reconnectWorld();
				}
			}
		}

		rs.close();
		ps.close();

		ps = con.prepareStatement("SELECT * FROM accounts WHERE id = ?");
		ps.setInt(1, ret.accountid);
		rs = ps.executeQuery();
		while (rs.next()) {
			ret.getClient().setAccountName(rs.getString("name"));
			ret.paypalnx = rs.getInt("paypalNX");
			ret.maplepoints = rs.getInt("mPoints");
			ret.cardnx = rs.getInt("cardNX");
		}
		rs.close();
		ps.close();

		String sql = "SELECT * FROM inventoryitems " + "LEFT JOIN inventoryequipment USING (inventoryitemid) " + "WHERE characterid = ?";
		if (!channelserver) {
			sql += " AND inventorytype = " + MapleInventoryType.EQUIPPED.getType();
		}
		ps = con.prepareStatement(sql);
		ps.setInt(1, charid);
		// PreparedStatement itemLog = con.prepareStatement("SELECT msg FROM inventorylog WHERE inventoryitemid = ?");
		rs = ps.executeQuery();
		while (rs.next()) {
			MapleInventoryType type = MapleInventoryType.getByType((byte) rs.getInt("inventorytype"));
			// itemLog.setInt(1, rs.getInt("inventoryitemid"));
			// ResultSet rsItemLog = itemLog.executeQuery();
			// IItem logItem;
			if (type.equals(MapleInventoryType.EQUIP) || type.equals(MapleInventoryType.EQUIPPED)) {
				int itemid = rs.getInt("itemid");
				Equip equip = new Equip(itemid, (byte) rs.getInt("position"), rs.getInt("ringid"));
				equip.setOwner(rs.getString("owner"));
				equip.setQuantity((short) rs.getInt("quantity"));
				equip.setAcc((short) rs.getInt("acc"));
				equip.setAvoid((short) rs.getInt("avoid"));
				equip.setDex((short) rs.getInt("dex"));
				equip.setHands((short) rs.getInt("hands"));
				equip.setHp((short) rs.getInt("hp"));
				equip.setInt((short) rs.getInt("int"));
				equip.setJump((short) rs.getInt("jump"));
				equip.setLuk((short) rs.getInt("luk"));
				equip.setMatk((short) rs.getInt("matk"));
				equip.setMdef((short) rs.getInt("mdef"));
				equip.setMp((short) rs.getInt("mp"));
				equip.setSpeed((short) rs.getInt("speed"));
				equip.setStr((short) rs.getInt("str"));
				equip.setWatk((short) rs.getInt("watk"));
				equip.setWdef((short) rs.getInt("wdef"));
				equip.setUpgradeSlots((byte) rs.getInt("upgradeslots"));
				equip.setLocked((byte) rs.getInt("locked"));
				equip.setLevel((byte) rs.getInt("level"));
				ret.getInventory(type).addFromDB(equip);
			// logItem = equip;
			} else {
				Item item = new Item(rs.getInt("itemid"), (byte) rs.getInt("position"), (short) rs.getInt("quantity"), rs.getInt("petid"));
				item.setOwner(rs.getString("owner"));
				ret.getInventory(type).addFromDB(item);
			// logItem = item;
			}
		// while (rsItemLog.next()) {
		// logItem.log(rsItemLog.getString("msg"), true);
		// }
		// rsItemLog.close();
		}
		rs.close();
		ps.close();
		// itemLog.close();

		if (channelserver) {
			ps = con.prepareStatement("SELECT * FROM queststatus WHERE characterid = ?");
			ps.setInt(1, charid);
			rs = ps.executeQuery();
			PreparedStatement pse = con.prepareStatement("SELECT * FROM queststatusmobs WHERE queststatusid = ?");
			while (rs.next()) {
				MapleQuest q = MapleQuest.getInstance(rs.getInt("quest"));
				MapleQuestStatus status = new MapleQuestStatus(q, MapleQuestStatus.Status.getById(rs.getInt("status")));
				long cTime = rs.getLong("time");
				if (cTime > -1) {
					status.setCompletionTime(cTime * 1000);
				}
				status.setForfeited(rs.getInt("forfeited"));
				ret.quests.put(q, status);
				pse.setInt(1, rs.getInt("queststatusid"));
				ResultSet rsMobs = pse.executeQuery();
				while (rsMobs.next()) {
					status.setMobKills(rsMobs.getInt("mob"), rsMobs.getInt("count"));
				}
				rsMobs.close();
			}
			rs.close();
			ps.close();
			pse.close();

			ps = con.prepareStatement("SELECT skillid,skilllevel,masterlevel FROM skills WHERE characterid = ?");
			ps.setInt(1, charid);
			rs = ps.executeQuery();
			while (rs.next()) {
				ret.skills.put(SkillFactory.getSkill(rs.getInt("skillid")), new SkillEntry(rs.getInt("skilllevel"), rs.getInt("masterlevel")));
			}
			rs.close();
			ps.close();

			ps = con.prepareStatement("SELECT * FROM skillmacros WHERE characterid = ?");
			ps.setInt(1, charid);
			rs = ps.executeQuery();
			while (rs.next()) {
				int skill1 = rs.getInt("skill1");
				int skill2 = rs.getInt("skill2");
				int skill3 = rs.getInt("skill3");
				String name = rs.getString("name");
				int shout = rs.getInt("shout");
				int position = rs.getInt("position");
				SkillMacro macro = new SkillMacro(skill1, skill2, skill3, name, shout, position);
				ret.skillMacros[position] = macro;
			}
			rs.close();
			ps.close();

			ps = con.prepareStatement("SELECT `key`,`type`,`action` FROM keymap WHERE characterid = ?");
			ps.setInt(1, charid);
			rs = ps.executeQuery();
			while (rs.next()) {
				int key = rs.getInt("key");
				int type = rs.getInt("type");
				int action = rs.getInt("action");
				ret.keymap.put(Integer.valueOf(key), new MapleKeyBinding(type, action));
			}
			rs.close();
			ps.close();

			ps = con.prepareStatement("SELECT `locationtype`,`map` FROM savedlocations WHERE characterid = ?");
			ps.setInt(1, charid);
			rs = ps.executeQuery();
			while (rs.next()) {
				String locationType = rs.getString("locationtype");
				int mapid = rs.getInt("map");
				ret.savedLocations[SavedLocationType.valueOf(locationType).ordinal()] = mapid;
			}
			rs.close();
			ps.close();

			ps = con.prepareStatement("SELECT `characterid_to`,`when` FROM famelog WHERE characterid = ? AND DATEDIFF(NOW(),`when`) < 30");
			ps.setInt(1, charid);
			rs = ps.executeQuery();
			ret.lastfametime = 0;
			ret.lastmonthfameids = new ArrayList<Integer>(31);
			while (rs.next()) {
				ret.lastfametime = Math.max(ret.lastfametime, rs.getTimestamp("when").getTime());
				ret.lastmonthfameids.add(Integer.valueOf(rs.getInt("characterid_to")));
			}
			rs.close();
			ps.close();

			ret.buddylist.loadFromDb(charid);
			ret.storage = MapleStorage.loadOrCreateFromDB(ret.accountid);
		}

		ret.recalcLocalStats();
		ret.silentEnforceMaxHpMp();
		return ret;
	}

	public static MapleCharacter getDefault(MapleClient client, int chrid) {
		MapleCharacter ret = getDefault(client);
		ret.id = chrid;
		return ret;
	}

	public static MapleCharacter getDefault(MapleClient client) {
		MapleCharacter ret = new MapleCharacter();
		ret.client = client;
		ret.hp = 50;
		ret.maxhp = 50;
		ret.mp = 5;
		ret.maxmp = 5;
		ret.map = null;
		ret.exp.set(0);
		ret.gmLevel = 0;
		ret.job = MapleJob.BEGINNER;
		ret.meso.set(0);
		ret.level = 1;
		ret.accountid = client.getAccID();
		ret.buddylist = new BuddyList(20);
		ret.CP = 0;
		ret.totalCP = 0;
		ret.team = -1;
		try {
			Connection con = DatabaseConnection.getConnection();
			PreparedStatement ps;
			ps = con.prepareStatement("SELECT * FROM accounts WHERE id = ?");
			ps.setInt(1, ret.accountid);
			ResultSet rs = ps.executeQuery();
			rs = ps.executeQuery();
			while (rs.next()) {
				ret.getClient().setAccountName(rs.getString("name"));
				ret.paypalnx = rs.getInt("paypalNX");
				ret.maplepoints = rs.getInt("mPoints");
				ret.cardnx = rs.getInt("cardNX");
			}
			rs.close();
			ps.close();
		} catch (SQLException e) {
			log.error("ERROR", e);
		}
		ret.incs = false;
        ret.inmts = false;
		ret.APQScore = 0;

		ret.keymap.put(Integer.valueOf(2), new MapleKeyBinding(4, 10));
		ret.keymap.put(Integer.valueOf(3), new MapleKeyBinding(4, 12));
		ret.keymap.put(Integer.valueOf(4), new MapleKeyBinding(4, 13));
		ret.keymap.put(Integer.valueOf(5), new MapleKeyBinding(4, 18));
		ret.keymap.put(Integer.valueOf(6), new MapleKeyBinding(4, 24));
		ret.keymap.put(Integer.valueOf(7), new MapleKeyBinding(4, 21));
		ret.keymap.put(Integer.valueOf(16), new MapleKeyBinding(4, 8));
		ret.keymap.put(Integer.valueOf(17), new MapleKeyBinding(4, 5));
		ret.keymap.put(Integer.valueOf(18), new MapleKeyBinding(4, 0));
		ret.keymap.put(Integer.valueOf(19), new MapleKeyBinding(4, 4));
		ret.keymap.put(Integer.valueOf(23), new MapleKeyBinding(4, 1));
		ret.keymap.put(Integer.valueOf(25), new MapleKeyBinding(4, 19));
		ret.keymap.put(Integer.valueOf(26), new MapleKeyBinding(4, 14));
		ret.keymap.put(Integer.valueOf(27), new MapleKeyBinding(4, 15));
		ret.keymap.put(Integer.valueOf(29), new MapleKeyBinding(5, 52));
		ret.keymap.put(Integer.valueOf(31), new MapleKeyBinding(4, 2));
		ret.keymap.put(Integer.valueOf(34), new MapleKeyBinding(4, 17));
		ret.keymap.put(Integer.valueOf(35), new MapleKeyBinding(4, 11));
		ret.keymap.put(Integer.valueOf(37), new MapleKeyBinding(4, 3));
		ret.keymap.put(Integer.valueOf(38), new MapleKeyBinding(4, 20));
		ret.keymap.put(Integer.valueOf(40), new MapleKeyBinding(4, 16));
		ret.keymap.put(Integer.valueOf(41), new MapleKeyBinding(4, 23));
		ret.keymap.put(Integer.valueOf(43), new MapleKeyBinding(4, 9));
		ret.keymap.put(Integer.valueOf(44), new MapleKeyBinding(5, 50));
		ret.keymap.put(Integer.valueOf(45), new MapleKeyBinding(5, 51));
		ret.keymap.put(Integer.valueOf(46), new MapleKeyBinding(4, 6));
		ret.keymap.put(Integer.valueOf(48), new MapleKeyBinding(4, 22));
		ret.keymap.put(Integer.valueOf(50), new MapleKeyBinding(4, 7));
		ret.keymap.put(Integer.valueOf(56), new MapleKeyBinding(5, 53));
		ret.keymap.put(Integer.valueOf(57), new MapleKeyBinding(5, 54));
		ret.keymap.put(Integer.valueOf(59), new MapleKeyBinding(6, 100));
		ret.keymap.put(Integer.valueOf(60), new MapleKeyBinding(6, 101));
		ret.keymap.put(Integer.valueOf(61), new MapleKeyBinding(6, 102));
		ret.keymap.put(Integer.valueOf(62), new MapleKeyBinding(6, 103));
		ret.keymap.put(Integer.valueOf(63), new MapleKeyBinding(6, 104));
		ret.keymap.put(Integer.valueOf(64), new MapleKeyBinding(6, 105));
		ret.keymap.put(Integer.valueOf(65), new MapleKeyBinding(6, 106));

		ret.recalcLocalStats();

		return ret;
	}

	public void saveToDB(boolean update) {
		Connection con = DatabaseConnection.getConnection();
		try {
			// clients should not be able to log back before their old state is saved (see MapleClient#getLoginState) so we are save to switch to a very low isolation level here
			con.setTransactionIsolation(Connection.TRANSACTION_READ_UNCOMMITTED);
			// connections are thread local now, no need to synchronize anymore =)
			con.setAutoCommit(false);
			PreparedStatement ps;
                        if (update) {
				ps = con.prepareStatement("UPDATE characters "
					+ "SET level = ?, fame = ?, str = ?, dex = ?, luk = ?, `int` = ?, "
					+ "exp = ?, hp = ?, mp = ?, maxhp = ?, maxmp = ?, sp = ?, ap = ?, "
					+ "gm = ?, skincolor = ?, gender = ?, job = ?, hair = ?, face = ?, map = ?, "
					+ "meso = ?, hpApUsed = ?, mpApUsed = ?, spawnpoint = ?, party = ?, buddyCapacity = ?, messengerid = ?, messengerposition = ?, married = ?, partnerid = ?, cantalk = ?, zakumlvl = ?, marriagequest = ? WHERE id = ?");
			} else {
                            //31 inserts
				ps = con
					.prepareStatement("INSERT INTO characters ("
						+ "level, fame, str, dex, luk, `int`, exp, hp, mp, "
						+ "maxhp, maxmp, sp, ap, gm, skincolor, gender, job, hair, face, map, meso, hpApUsed, mpApUsed, spawnpoint, party, buddyCapacity, messengerid, messengerposition, married, partnerid, cantalk, zakumlvl, marriagequest, accountid, name, world"
						+ ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
			}
			ps.setInt(1, level);
			ps.setInt(2, fame);
			ps.setInt(3, str);
			ps.setInt(4, dex);
			ps.setInt(5, luk);
			ps.setInt(6, int_);
			ps.setInt(7, exp.get());
			ps.setInt(8, hp);
			ps.setInt(9, mp);
			ps.setInt(10, maxhp);
			ps.setInt(11, maxmp);
			ps.setInt(12, remainingSp);
			ps.setInt(13, remainingAp);
			ps.setInt(14, gmLevel);
			ps.setInt(15, skinColor.getId());
			ps.setInt(16, gender);
			ps.setInt(17, job.getId());
			ps.setInt(18, hair);
			ps.setInt(19, face);
			if (map == null) {
				ps.setInt(20, 0);
			} else {
				ps.setInt(20, map.getId());
			}
			ps.setInt(21, meso.get());
			ps.setInt(22, hpApUsed);
			ps.setInt(23, mpApUsed);
			if (map == null || map.getId() == 610020000 || map.getId() == 610020001) {
				ps.setInt(24, 0);
			} else {
				MaplePortal closest = map.findClosestSpawnpoint(getPosition());
				if (closest != null) {
					ps.setInt(24, closest.getId());
				} else {
					ps.setInt(24, 0);
				}
			}
			if (party != null) {
				ps.setInt(25, party.getId());
			} else {
				ps.setInt(25, -1);
			}
			ps.setInt(26, buddylist.getCapacity());

                        if (messenger != null) {
                                ps.setInt(27, messenger.getId());
                                ps.setInt(28, messengerposition);
                        }
                        else {
                                ps.setInt(27, 0);
                                ps.setInt(28, 4);
                        }

                        ps.setInt(29, married);
                        ps.setInt(30, partnerid);
                        ps.setInt(31, canTalk);
                        if (zakumLvl <= 2) //Don't let zakumLevel exceed three ;)
                            ps.setInt(32, zakumLvl);
                        else
                            ps.setInt(32, 2);
                        ps.setInt(33, marriageQuestLevel);
			if (update) {
				ps.setInt(34, id);
			} else {
				ps.setInt(34, accountid);
                                ps.setString(35, name);
				ps.setInt(36, world); // TODO store world somewhere ;)
			}
			int updateRows = ps.executeUpdate();
			if (!update) {
				ResultSet rs = ps.getGeneratedKeys();
				if (rs.next()) {
					this.id = rs.getInt(1);
				} else {
					throw new DatabaseException("Inserting char failed.");
				}
			} else if (updateRows < 1) {
				throw new DatabaseException("Character not in database (" + id + ")");
			}
			ps.close();

			for (int i = 0; i < 3; i++) {
				if (pets[i] != null) {
					pets[i].saveToDb();
				}
			}

			ps = con.prepareStatement("DELETE FROM skillmacros WHERE characterid = ?");
			ps.setInt(1, id);
			ps.executeUpdate();
			ps.close();

			for (int i = 0; i < 5; i++) {
				SkillMacro macro = skillMacros[i];
				if (macro != null) {
					ps = con.prepareStatement("INSERT INTO skillmacros (characterid, skill1, skill2, skill3, name, shout, position) VALUES (?, ?, ?, ?, ?, ?, ?)");

					ps.setInt(1, id);
					ps.setInt(2, macro.getSkill1());
					ps.setInt(3, macro.getSkill2());
					ps.setInt(4, macro.getSkill3());
					ps.setString(5, macro.getName());
					ps.setInt(6, macro.getShout());
					ps.setInt(7, i);

					ps.executeUpdate();
					ps.close();
				}
			}

			ps = con.prepareStatement("DELETE FROM inventoryitems WHERE characterid = ?");
			ps.setInt(1, id);
			ps.executeUpdate();
			ps.close();

			ps = con.prepareStatement("INSERT INTO inventoryitems (characterid, itemid, inventorytype, position, quantity, owner, petid) VALUES (?, ?, ?, ?, ?, ?, ?)");
			PreparedStatement pse = con.prepareStatement("INSERT INTO inventoryequipment VALUES (DEFAULT, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
			// PreparedStatement psl = con.prepareStatement("INSERT INTO inventorylog VALUES (DEFAULT, ?, ?)");
			for (MapleInventory iv : inventory) {
				ps.setInt(3, iv.getType().getType());
				for (IItem item : iv.list()) {
					// ps.setInt(1, item.getId());
					ps.setInt(1, id);
					ps.setInt(2, item.getItemId());
					ps.setInt(4, item.getPosition());
					ps.setInt(5, item.getQuantity());
					ps.setString(6, item.getOwner());
					ps.setInt(7, item.getPetId());
					ps.executeUpdate();
					ResultSet rs = ps.getGeneratedKeys();
					int itemid;
					if (rs.next()) {
						itemid = rs.getInt(1);
					} else {
						throw new DatabaseException("Inserting char failed.");
					}
					// for (String msg : item.getLog()) {
					// psl.setInt(1, itemid);
					// psl.setString(2, msg);
					// psl.executeUpdate();
					// }

					if (iv.getType().equals(MapleInventoryType.EQUIP) || iv.getType().equals(MapleInventoryType.EQUIPPED)) {
						pse.setInt(1, itemid);
						IEquip equip = (IEquip) item;
						pse.setInt(2, equip.getUpgradeSlots());
						pse.setInt(3, equip.getLevel());
						pse.setInt(4, equip.getStr());
						pse.setInt(5, equip.getDex());
						pse.setInt(6, equip.getInt());
						pse.setInt(7, equip.getLuk());
						pse.setInt(8, equip.getHp());
						pse.setInt(9, equip.getMp());
						pse.setInt(10, equip.getWatk());
						pse.setInt(11, equip.getMatk());
						pse.setInt(12, equip.getWdef());
						pse.setInt(13, equip.getMdef());
						pse.setInt(14, equip.getAcc());
						pse.setInt(15, equip.getAvoid());
						pse.setInt(16, equip.getHands());
						pse.setInt(17, equip.getSpeed());
						pse.setInt(18, equip.getJump());
						pse.setInt(19, equip.getRingId());
						pse.setInt(20, equip.getLocked());
						pse.executeUpdate();
					}
				}
			}
			ps.close();
			pse.close();

			deleteWhereCharacterId(con, "DELETE FROM queststatus WHERE characterid = ?");
			ps = con.prepareStatement("INSERT INTO queststatus (`queststatusid`, `characterid`, `quest`, `status`, `time`, `forfeited`) VALUES (DEFAULT, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS);
			pse = con.prepareStatement("INSERT INTO queststatusmobs VALUES (DEFAULT, ?, ?, ?)");
			ps.setInt(1, id);
			for (MapleQuestStatus q : quests.values()) {
				ps.setInt(2, q.getQuest().getId());
				ps.setInt(3, q.getStatus().getId());
				ps.setInt(4, (int) (q.getCompletionTime() / 1000));
				ps.setInt(5, q.getForfeited());
				ps.executeUpdate();
				ResultSet rs = ps.getGeneratedKeys();
				rs.next();
				for (int mob : q.getMobKills().keySet()) {
					pse.setInt(1, rs.getInt(1));
					pse.setInt(2, mob);
					pse.setInt(3, q.getMobKills(mob));
					pse.executeUpdate();
				}
				rs.close();
			}
			ps.close();
			pse.close();


			deleteWhereCharacterId(con, "DELETE FROM skills WHERE characterid = ?");
			ps = con.prepareStatement("INSERT INTO skills (characterid, skillid, skilllevel, masterlevel) VALUES (?, ?, ?, ?)");
			ps.setInt(1, id);
			for (Entry<ISkill, SkillEntry> skill : skills.entrySet()) {
				ps.setInt(2, skill.getKey().getId());
				ps.setInt(3, skill.getValue().skillevel);
				ps.setInt(4, skill.getValue().masterlevel);
				ps.executeUpdate();
			}
			ps.close();

			deleteWhereCharacterId(con, "DELETE FROM keymap WHERE characterid = ?");
			ps = con.prepareStatement("INSERT INTO keymap (characterid, `key`, `type`, `action`) VALUES (?, ?, ?, ?)");
			ps.setInt(1, id);
			for (Entry<Integer, MapleKeyBinding> keybinding : keymap.entrySet()) {
				ps.setInt(2, keybinding.getKey().intValue());
				ps.setInt(3, keybinding.getValue().getType());
				ps.setInt(4, keybinding.getValue().getAction());
				ps.executeUpdate();
			}
			ps.close();

			deleteWhereCharacterId(con, "DELETE FROM savedlocations WHERE characterid = ?");
			ps = con.prepareStatement("INSERT INTO savedlocations (characterid, `locationtype`, `map`) VALUES (?, ?, ?)");
			ps.setInt(1, id);
			for (SavedLocationType savedLocationType : SavedLocationType.values()) {
				if (savedLocations[savedLocationType.ordinal()] != -1) {
					ps.setString(2, savedLocationType.name());
					ps.setInt(3, savedLocations[savedLocationType.ordinal()]);
					ps.executeUpdate();
				}
			}
			ps.close();

			deleteWhereCharacterId(con, "DELETE FROM buddies WHERE characterid = ? AND pending = 0");
			ps = con.prepareStatement("INSERT INTO buddies (characterid, `buddyid`, `pending`) VALUES (?, ?, 0)");
			ps.setInt(1, id);
			for (BuddylistEntry entry : buddylist.getBuddies()) {
				if (entry.isVisible()) {
					ps.setInt(2, entry.getCharacterId());
					ps.executeUpdate();
				}
			}
			ps.close();

			ps = con.prepareStatement("UPDATE accounts SET `paypalNX` = ?, `mPoints` = ?, `cardNX` = ? WHERE id = ?");
			ps.setInt(1, paypalnx);
			ps.setInt(2, maplepoints);
			ps.setInt(3, cardnx);
			ps.setInt(4, client.getAccID());
			ps.executeUpdate();
			ps.close();

			if (storage != null) {
				storage.saveToDB();
			}

			con.commit();
		} catch (Exception e) {
			log.error(MapleClient.getLogMessage(this, "[charsave] Error saving character data"), e);
			try {
				con.rollback();
			} catch (SQLException e1) {
				log.error(MapleClient.getLogMessage(this, "[charsave] Error Rolling Back"), e);
			}
		} finally {
			try {
				con.setAutoCommit(true);
				con.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
			} catch (SQLException e) {
				log.error(MapleClient.getLogMessage(this, "[charsave] Error going back to autocommit mode"), e);
			}
		}
	}

	private void deleteWhereCharacterId(Connection con, String sql) throws SQLException {
		PreparedStatement ps = con.prepareStatement(sql);
		ps.setInt(1, id);
		ps.executeUpdate();
		ps.close();
	}

	public MapleQuestStatus getQuest(MapleQuest quest) {
		if (!quests.containsKey(quest)) {
			return new MapleQuestStatus(quest, MapleQuestStatus.Status.NOT_STARTED);
		}
		return quests.get(quest);
	}

	public void updateQuest(MapleQuestStatus quest) {
		quests.put(quest.getQuest(), quest);
		if (!(quest.getQuest() instanceof MapleCustomQuest)) {
			if (quest.getStatus().equals(MapleQuestStatus.Status.STARTED)) {
				client.getSession().write(MaplePacketCreator.startQuest(this, (short) quest.getQuest().getId()));
				client.getSession().write(MaplePacketCreator.updateQuestInfo(this, (short) quest.getQuest().getId(), quest.getNpc(), (byte) 8));
			} else if (quest.getStatus().equals(MapleQuestStatus.Status.COMPLETED)) {
				client.getSession().write(MaplePacketCreator.completeQuest(this, (short) quest.getQuest().getId()));
			} else if (quest.getStatus().equals(MapleQuestStatus.Status.NOT_STARTED)) {
				client.getSession().write(MaplePacketCreator.forfeitQuest(this, (short) quest.getQuest().getId()));
			}
		}
	}

	public static int getIdByName(String name, int world) {
		Connection con = DatabaseConnection.getConnection();
		PreparedStatement ps;
		try {
			ps = con.prepareStatement("SELECT id FROM characters WHERE name = ? AND world = ?");
			ps.setString(1, name);
			ps.setInt(2, world);
			ResultSet rs = ps.executeQuery();
			if (!rs.next()) {
				ps.close();
				return -1;
			}
			int id = rs.getInt("id");
			ps.close();
			return id;
		} catch (SQLException e) {
			log.error("ERROR", e);
		}
		return -1;
	}

	public boolean isActiveBuffedValue(int skillid) {
		LinkedList<MapleBuffStatValueHolder> allBuffs = new LinkedList<MapleBuffStatValueHolder>(effects.values());
		for (MapleBuffStatValueHolder mbsvh : allBuffs) {
			if (mbsvh.effect.isSkill() && mbsvh.effect.getSourceId() == skillid) {
				return true;
			}
		}
		return false;
	}

	public Integer getBuffedValue(MapleBuffStat effect) {
		MapleBuffStatValueHolder mbsvh = effects.get(effect);
		if (mbsvh == null) {
			return null;
		}
		return Integer.valueOf(mbsvh.value);
	}

	public boolean isBuffFrom(MapleBuffStat stat, ISkill skill) {
		MapleBuffStatValueHolder mbsvh = effects.get(stat);
		if (mbsvh == null) {
			return false;
		}
		return mbsvh.effect.isSkill() && mbsvh.effect.getSourceId() == skill.getId();
	}

	public int getBuffSource(MapleBuffStat stat) {
		MapleBuffStatValueHolder mbsvh = effects.get(stat);
		if (mbsvh == null) {
			return -1;
		}
		return mbsvh.effect.getSourceId();
	}

	public int getItemQuantity(int itemid, boolean checkEquipped) {
		MapleInventoryType type = MapleItemInformationProvider.getInstance().getInventoryType(itemid);
		MapleInventory iv = inventory[type.ordinal()];
		int possesed = iv.countById(itemid);
		if (checkEquipped) {
			possesed += inventory[MapleInventoryType.EQUIPPED.ordinal()].countById(itemid);
		}

		return possesed;
	}

	public void setBuffedValue(MapleBuffStat effect, int value) {
		MapleBuffStatValueHolder mbsvh = effects.get(effect);
		if (mbsvh == null) {
			return;
		}
		mbsvh.value = value;
	}

	public Long getBuffedStarttime(MapleBuffStat effect) {
		MapleBuffStatValueHolder mbsvh = effects.get(effect);
		if (mbsvh == null) {
			return null;
		}
		return Long.valueOf(mbsvh.startTime);
	}

	public MapleStatEffect getStatForBuff(MapleBuffStat effect) {
		MapleBuffStatValueHolder mbsvh = effects.get(effect);
		if (mbsvh == null) {
			return null;
		}
		return mbsvh.effect;
	}

	private void prepareDragonBlood(final MapleStatEffect bloodEffect) {
		if (dragonBloodSchedule != null) {
			dragonBloodSchedule.cancel(false);
		}
		dragonBloodSchedule = TimerManager.getInstance().register(new Runnable() {

			@Override
			public void run() {
				addHP(-bloodEffect.getX());
				getClient().getSession().write(MaplePacketCreator.showOwnBuffEffect(bloodEffect.getSourceId(), 5));
				getMap().broadcastMessage(MapleCharacter.this, MaplePacketCreator.showBuffeffect(getId(), bloodEffect.getSourceId(), 5, (byte) 3), false);
				checkBerserk();
			}
		}, 4000, 4000);
	}

	public void startFullnessSchedule(final int decrease, final MaplePet pet, int petSlot) {
		ScheduledFuture<?> schedule = TimerManager.getInstance().register(new Runnable() {

			@Override
			public void run() {
				int newFullness = pet.getFullness() - decrease;
				if (newFullness <= 5) {
					pet.setFullness(15);
					unequipPet(pet, true, true);
				} else {
					pet.setFullness(newFullness);
					getClient().getSession().write(MaplePacketCreator.updatePet(pet, true));
				}
			}
		}, 60000, 60000);
		switch (petSlot) {
			case 0:
				fullnessSchedule = schedule;
			case 1:
				fullnessSchedule_1 = schedule;
			case 2:
				fullnessSchedule_2 = schedule;
		}
	}

	public void cancelFullnessSchedule(int petSlot) {
		switch (petSlot) {
			case 0:
				fullnessSchedule.cancel(false);
			case 1:
				fullnessSchedule_1.cancel(false);
			case 2:
				fullnessSchedule_2.cancel(false);
		}
	}

	public void startMapTimeLimitTask(final MapleMap from, final MapleMap to) {
		if (to.getTimeLimit() > 0 && from != null) {
			final MapleCharacter chr = this;
			mapTimeLimitTask = TimerManager.getInstance().register(new Runnable() {

				@Override
				public void run() {
					MaplePortal pfrom = null;
					if (MapleItemInformationProvider.getInstance().isMiniDungeonMap(from.getId())) {
						pfrom = from.getPortal("MD00");
					} else {
						pfrom = from.getPortal(0);
					}
					if (pfrom != null) {
						chr.changeMap(from, pfrom);
					}
				}
			}, from.getTimeLimit() * 1000, from.getTimeLimit() * 1000);
		}
	}

	public void cancelMapTimeLimitTask() {
		if (mapTimeLimitTask != null) {
			mapTimeLimitTask.cancel(false);
		}
	}

	public void registerEffect(MapleStatEffect effect, long starttime, ScheduledFuture<?> schedule) {
		if (effect.isHide()) {
			this.hidden = true;
			getMap().broadcastMessage(this, MaplePacketCreator.removePlayerFromMap(getId()), false);
		} else if (effect.isDragonBlood()) {
			prepareDragonBlood(effect);
		} else if (effect.isBerserk()) {
			checkBerserk();
		} else if (effect.isBeholder()) {
			prepareBeholderEffect();
		}
		for (Pair<MapleBuffStat, Integer> statup : effect.getStatups()) {
			effects.put(statup.getLeft(), new MapleBuffStatValueHolder(effect, starttime, schedule, statup.getRight().intValue()));
		}

		recalcLocalStats();
	}

	private List<MapleBuffStat> getBuffStats(MapleStatEffect effect, long startTime) {
		List<MapleBuffStat> stats = new ArrayList<MapleBuffStat>();
		for (Entry<MapleBuffStat, MapleBuffStatValueHolder> stateffect : effects.entrySet()) {
			MapleBuffStatValueHolder mbsvh = stateffect.getValue();
			if (mbsvh.effect.sameSource(effect) && (startTime == -1 || startTime == mbsvh.startTime)) {
				stats.add(stateffect.getKey());
			}
		}
		return stats;
	}

	private void deregisterBuffStats(List<MapleBuffStat> stats) {
		List<MapleBuffStatValueHolder> effectsToCancel = new ArrayList<MapleBuffStatValueHolder>(stats.size());
		for (MapleBuffStat stat : stats) {
			MapleBuffStatValueHolder mbsvh = effects.get(stat);
			if (mbsvh != null) {
				effects.remove(stat);
				boolean addMbsvh = true;
				for (MapleBuffStatValueHolder contained : effectsToCancel) {
					if (mbsvh.startTime == contained.startTime && contained.effect == mbsvh.effect) {
						addMbsvh = false;
					}
				}
				if (addMbsvh) {
					effectsToCancel.add(mbsvh);
				}
				if (stat == MapleBuffStat.SUMMON || stat == MapleBuffStat.PUPPET) {
					int summonId = mbsvh.effect.getSourceId();
					MapleSummon summon = summons.get(summonId);
					if (summon != null) {
						getMap().broadcastMessage(MaplePacketCreator.removeSpecialMapObject(summon, true));
						getMap().removeMapObject(summon);
						removeVisibleMapObject(summon);
						summons.remove(summonId);
					}
					if (summon.getSkill() == 1321007) {
						if (beholderHealingSchedule != null) {
							beholderHealingSchedule.cancel(false);
							beholderHealingSchedule = null;
						}
						if (beholderBuffSchedule != null) {
							beholderBuffSchedule.cancel(false);
							beholderBuffSchedule = null;
						}
					}
				} else if (stat == MapleBuffStat.DRAGONBLOOD) {
					dragonBloodSchedule.cancel(false);
					dragonBloodSchedule = null;
				}
			}
		}
		for (MapleBuffStatValueHolder cancelEffectCancelTasks : effectsToCancel) {
			if (getBuffStats(cancelEffectCancelTasks.effect, cancelEffectCancelTasks.startTime).size() == 0) {
				cancelEffectCancelTasks.schedule.cancel(false);
			}
		}
	}

	/**
	 * @param effect
	 * @param overwrite when overwrite is set no data is sent and all the Buffstats in the StatEffect are deregistered
	 * @param startTime
	 */
	public void cancelEffect(MapleStatEffect effect, boolean overwrite, long startTime) {
		List<MapleBuffStat> buffstats;
		if (!overwrite) {
			buffstats = getBuffStats(effect, startTime);
		} else {
			List<Pair<MapleBuffStat, Integer>> statups = effect.getStatups();
			buffstats = new ArrayList<MapleBuffStat>(statups.size());
			for (Pair<MapleBuffStat, Integer> statup : statups) {
				buffstats.add(statup.getLeft());
			}
		}
		deregisterBuffStats(buffstats);
		if (effect.isMagicDoor()) {
			// remove for all on maps
			if (!getDoors().isEmpty()) {
				MapleDoor door = getDoors().iterator().next();
				for (MapleCharacter chr : door.getTarget().getCharacters()) {
					door.sendDestroyData(chr.getClient());
				}
				for (MapleCharacter chr : door.getTown().getCharacters()) {
					door.sendDestroyData(chr.getClient());
				}
				for (MapleDoor destroyDoor : getDoors()) {
					door.getTarget().removeMapObject(destroyDoor);
					door.getTown().removeMapObject(destroyDoor);
				}
				clearDoors();
				silentPartyUpdate();
			}
		}

		// check if we are still logged in o.o
		if (!overwrite) {
			cancelPlayerBuffs(buffstats);
			if (effect.isHide() && (MapleCharacter) getMap().getMapObject(getObjectId()) != null) {
				this.hidden = false;
				getMap().broadcastMessage(this, MaplePacketCreator.spawnPlayerMapobject(this), false);
				for (int i = 0; i < 3; i++) {
					if (pets[i] != null) {
						getMap().broadcastMessage(this, MaplePacketCreator.showPet(this, pets[i], false, false), false);
					}
				}
			}
		}
	}

	public void cancelBuffStats(MapleBuffStat stat) {
		List<MapleBuffStat> buffStatList = Arrays.asList(stat);
		deregisterBuffStats(buffStatList);
		cancelPlayerBuffs(buffStatList);
	}

	public void cancelEffectFromBuffStat(MapleBuffStat stat) {
		cancelEffect(effects.get(stat).effect, false, -1);
	}

	private void cancelPlayerBuffs(List<MapleBuffStat> buffstats) {
		if (getClient().getChannelServer().getPlayerStorage().getCharacterById(getId()) != null) { // are we still connected ?
			recalcLocalStats();
			enforceMaxHpMp();
			getClient().getSession().write(MaplePacketCreator.cancelBuff(buffstats));
			getMap().broadcastMessage(this, MaplePacketCreator.cancelForeignBuff(getId(), buffstats), false);
		}
	}

	public void dispel() {
		LinkedList<MapleBuffStatValueHolder> allBuffs = new LinkedList<MapleBuffStatValueHolder>(effects.values());
		for (MapleBuffStatValueHolder mbsvh : allBuffs) {
			if (mbsvh.effect.isSkill()) {
				cancelEffect(mbsvh.effect, false, mbsvh.startTime);
			}
		}
	}

	public void cancelAllBuffs() {
		LinkedList<MapleBuffStatValueHolder> allBuffs = new LinkedList<MapleBuffStatValueHolder>(effects.values());
		for (MapleBuffStatValueHolder mbsvh : allBuffs) {
			cancelEffect(mbsvh.effect, false, mbsvh.startTime);
		}
	}

	public void cancelMorphs() {
		LinkedList<MapleBuffStatValueHolder> allBuffs = new LinkedList<MapleBuffStatValueHolder>(effects.values());
		for (MapleBuffStatValueHolder mbsvh : allBuffs) {
			if (mbsvh.effect.isMorph() && mbsvh.effect.getSourceId() != 5111005 && mbsvh.effect.getSourceId() != 5121003) {
				cancelEffect(mbsvh.effect, false, mbsvh.startTime);
			}
		}
	}

	public void silentGiveBuffs(List<PlayerBuffValueHolder> buffs) {
		for (PlayerBuffValueHolder mbsvh : buffs) {
			mbsvh.effect.silentApplyBuff(this, mbsvh.startTime);
		}
	}

	public List<PlayerBuffValueHolder> getAllBuffs() {
		List<PlayerBuffValueHolder> ret = new ArrayList<PlayerBuffValueHolder>();
		for (MapleBuffStatValueHolder mbsvh : effects.values()) {
			ret.add(new PlayerBuffValueHolder(mbsvh.startTime, mbsvh.effect));
		}
		return ret;
	}

	public void cancelMagicDoor() {
		LinkedList<MapleBuffStatValueHolder> allBuffs = new LinkedList<MapleBuffStatValueHolder>(effects.values());
		for (MapleBuffStatValueHolder mbsvh : allBuffs) {
			if (mbsvh.effect.isMagicDoor()) {
				cancelEffect(mbsvh.effect, false, mbsvh.startTime);
			}
		}
	}

	public void handleOrbgain() {
		MapleStatEffect ceffect = null;
		int advComboSkillLevel = getSkillLevel(SkillFactory.getSkill(1120003));
		if (advComboSkillLevel > 0) {
			ceffect = SkillFactory.getSkill(1120003).getEffect(advComboSkillLevel);
		} else {
			ceffect = SkillFactory.getSkill(1111002).getEffect(getSkillLevel(SkillFactory.getSkill(1111002)));
		}

		if (getBuffedValue(MapleBuffStat.COMBO) < ceffect.getX() + 1) {
			int neworbcount = getBuffedValue(MapleBuffStat.COMBO) + 1;
			if (advComboSkillLevel > 0 && ceffect.makeChanceResult()) {
				if (neworbcount < ceffect.getX() + 1) {
					neworbcount++;
				}
			}

			List<Pair<MapleBuffStat, Integer>> stat = Collections.singletonList(new Pair<MapleBuffStat, Integer>(MapleBuffStat.COMBO, neworbcount));
			setBuffedValue(MapleBuffStat.COMBO, neworbcount);
			int duration = ceffect.getDuration();
			duration += (int) ((getBuffedStarttime(MapleBuffStat.COMBO) - System.currentTimeMillis()));

			getClient().getSession().write(MaplePacketCreator.giveBuff(1111002, duration, stat));
			getMap().broadcastMessage(this, MaplePacketCreator.giveForeignBuff(getId(), stat, ceffect), false);
		}
	}

	public void handleOrbconsume() {
		ISkill combo = SkillFactory.getSkill(1111002);
		MapleStatEffect ceffect = combo.getEffect(getSkillLevel(combo));
		List<Pair<MapleBuffStat, Integer>> stat = Collections.singletonList(new Pair<MapleBuffStat, Integer>(MapleBuffStat.COMBO, 1));
		setBuffedValue(MapleBuffStat.COMBO, 1);
		int duration = ceffect.getDuration();
		duration += (int) ((getBuffedStarttime(MapleBuffStat.COMBO) - System.currentTimeMillis()));

		getClient().getSession().write(MaplePacketCreator.giveBuff(1111002, duration, stat));
		getMap().broadcastMessage(this, MaplePacketCreator.giveForeignBuff(getId(), stat, ceffect), false);
	}

	private void silentEnforceMaxHpMp() {
		setMp(getMp());
		setHp(getHp(), true);
	}

	private void enforceMaxHpMp() {
		List<Pair<MapleStat, Integer>> stats = new ArrayList<Pair<MapleStat, Integer>>(2);
		if (getMp() > getCurrentMaxMp()) {
			setMp(getMp());
			stats.add(new Pair<MapleStat, Integer>(MapleStat.MP, Integer.valueOf(getMp())));
		}
		if (getHp() > getCurrentMaxHp()) {
			setHp(getHp());
			stats.add(new Pair<MapleStat, Integer>(MapleStat.HP, Integer.valueOf(getHp())));
		}
		if (stats.size() > 0) {
			getClient().getSession().write(MaplePacketCreator.updatePlayerStats(stats));
		}
	}

	public MapleMap getMap() {
		return map;
	}

	/**
	 * only for tests
	 *
	 * @param newmap
	 */
	public void setMap(MapleMap newmap) {
		this.map = newmap;
	}

	public int getMapId() {
		if (map != null) {
			return map.getId();
		}
		return mapid;
	}

	public int getInitialSpawnpoint() {
		return initialSpawnPoint;
	}

	public int getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public int getLevel() {
		return level;
	}

	public int getRank() {
		return rank;
	}

	public int getRankMove() {
		return rankMove;
	}

	public int getJobRank() {
		return jobRank;
	}

	public int getJobRankMove() {
		return jobRankMove;
	}

	public int getAPQScore() {
		return APQScore;
	}

	public int getFame() {
		return fame;
	}

	public int getCP() {
		return this.CP;
	}

	public int getTeam() {
		return this.team;
	}

	public int getTotalCP() {
		return this.totalCP;
	}

	public void setCP(int cp) {
		this.CP = cp;
	}

	public void setTeam(int team) {
		this.team = team;
	}

	public void setTotalCP(int totalcp) {
		this.totalCP = totalcp;
	}

	public void gainCP(int gain) {
		this.setCP(this.getCP() + gain);
		if (this.getCP() > this.getTotalCP()) {
			this.setTotalCP(this.getCP());
		}
		this.getClient().getSession().write(MaplePacketCreator.CPUpdate(false, this.getCP(), this.getTotalCP(), this.getTeam()));
		if (this.getParty() != null && this.getParty().getTeam() != -1) {
			this.getMap().broadcastMessage(MaplePacketCreator.CPUpdate(true, this.getParty().getCP(), this.getParty().getTotalCP(), this.getParty().getTeam()));
		}
	}

	public int getStr() {
		return str;
	}

	public int getDex() {
		return dex;
	}

	public int getLuk() {
		return luk;
	}

	public int getInt() {
		return int_;
	}

	public MapleClient getClient() {
		return client;
	}

	public int getExp() {
		return exp.get();
	}

	public int getHp() {
		return hp;
	}

	public int getMaxHp() {
		return maxhp;
	}

	public int getMp() {
		return mp;
	}

	public int getMaxMp() {
		return maxmp;
	}

	public int getRemainingAp() {
		return remainingAp;
	}

	public int getRemainingSp() {
		return remainingSp;
	}

	public int getMpApUsed() {
		return mpApUsed;
	}

	public void setMpApUsed(int mpApUsed) {
		this.mpApUsed = mpApUsed;
	}

	public int getHpApUsed() {
		return hpApUsed;
	}

	public boolean isHidden() {
		return hidden;
	}

	public void setHpApUsed(int hpApUsed) {
		this.hpApUsed = hpApUsed;
	}

	public MapleSkinColor getSkinColor() {
		return skinColor;
	}

	public MapleJob getJob() {
		return job;
	}

	public int getGender() {
		return gender;
	}

	public int getHair() {
		return hair;
	}

	public int getFace() {
		return face;
	}

	public void setName(String name) {
		this.name = name;
	}

	public void setStr(int str) {
		this.str = str;
		recalcLocalStats();
	}

	public void setDex(int dex) {
		this.dex = dex;
		recalcLocalStats();
	}

	public void setLuk(int luk) {
		this.luk = luk;
		recalcLocalStats();
	}

	public void setInt(int int_) {
		this.int_ = int_;
		recalcLocalStats();
	}

	public void setExp(int exp) {
		this.exp.set(exp);
	}

	public void setMaxHp(int hp) {
		this.maxhp = hp;
		recalcLocalStats();
	}

	public void setMaxMp(int mp) {
		this.maxmp = mp;
		recalcLocalStats();
	}

	public void setHair(int hair) {
		this.hair = hair;
	}

	public void setFace(int face) {
		this.face = face;
	}

	public void setFame(int fame) {
		this.fame = fame;
	}

	public void setAPQScore(int score) {
		this.APQScore = score;
	}

	public void setRemainingAp(int remainingAp) {
		this.remainingAp = remainingAp;
	}

	public void setRemainingSp(int remainingSp) {
		this.remainingSp = remainingSp;
	}

	public void setSkinColor(MapleSkinColor skinColor) {
		this.skinColor = skinColor;
	}

	public void setGender(int gender) {
		this.gender = gender;
	}

	public void setGM(int gmlevel) {
		this.gmLevel = gmlevel;
	}

	public CheatTracker getCheatTracker() {
		return anticheat;
	}

	public BuddyList getBuddylist() {
		return buddylist;
	}

	public void addFame(int famechange) {
		this.fame += famechange;
	}

	public void changeMap(final MapleMap to, final Point pos) {
		/*getClient().getSession().write(MaplePacketCreator.spawnPortal(map.getId(), to.getId(), pos));
		if (getParty() != null) {
		getClient().getSession().write(MaplePacketCreator.partyPortal(map.getId(), to.getId(), pos));
		}*/
		MaplePacket warpPacket = MaplePacketCreator.getWarpToMap(to, 0x80, this);
		changeMapInternal(to, pos, warpPacket);
	}

	public void changeMap(final MapleMap to, final MaplePortal pto) {
		if (to.getId() == 100000200 || to.getId() == 211000100 || to.getId() == 220000300) {
			MaplePacket warpPacket = MaplePacketCreator.getWarpToMap(to, pto.getId() - 2, this);
			changeMapInternal(to, pto.getPosition(), warpPacket);
		} else {
			MaplePacket warpPacket = MaplePacketCreator.getWarpToMap(to, pto.getId(), this);
			changeMapInternal(to, pto.getPosition(), warpPacket);
		}
	}

	private void changeMapInternal(final MapleMap to, final Point pos, MaplePacket warpPacket) {
		warpPacket.setOnSend(new Runnable() {

			@Override
			public void run() {
				map.removePlayer(MapleCharacter.this);
				if (getClient().getChannelServer().getPlayerStorage().getCharacterById(getId()) != null) {
					map = to;
					setPosition(pos);
					to.addPlayer(MapleCharacter.this);
					if (party != null) {
						silentPartyUpdate();
						getClient().getSession().write(MaplePacketCreator.updateParty(getClient().getChannel(), party, PartyOperation.SILENT_UPDATE, null));
						updatePartyMemberHP();
					}
					if (getMap().getHPDec() > 0) {
						hpDecreaseTask = TimerManager.getInstance().schedule(new Runnable() {

							@Override
							public void run() {
								doHurtHp();
							}
						}, 10000);
					}
					if (to.getId() == 980000301) { //todo: all cpq map id's
						setTeam(MapleCharacter.rand(0, 1));
						getClient().getSession().write(MaplePacketCreator.startMonsterCarnival(getTeam()));
					}
				}
			}
		});
		getClient().getSession().write(warpPacket);
	}

	public void leaveMap() {
		controlled.clear();
		visibleMapObjects.clear();
		if (chair != 0) {
			chair = 0;
		}
		if (hpDecreaseTask != null) {
			hpDecreaseTask.cancel(false);
		}
	}

	public void doHurtHp() {
		if (this.getInventory(MapleInventoryType.EQUIPPED).findById(getMap().getHPDecProtect()) != null) {
			return;
		}
		addHP(-getMap().getHPDec());
		hpDecreaseTask = TimerManager.getInstance().schedule(new Runnable() {

			@Override
			public void run() {
				doHurtHp();
			}
		}, 10000);
	}

	public void changeJob(MapleJob newJob) {
		this.job = newJob;
		this.remainingSp++;
		if (newJob.getId() % 10 == 2) {
			this.remainingSp += 2;
		}
		updateSingleStat(MapleStat.AVAILABLESP, this.remainingSp);
		updateSingleStat(MapleStat.JOB, newJob.getId());
		switch (this.job.getId()) {
			case 100:
				maxhp += rand(200, 250);
				break;
			case 200:
				maxmp += rand(100, 150);
				break;
			case 300:
			case 400:
			case 500:
				maxhp += rand(100, 150);
				maxmp += rand(25, 50);
				break;
			case 110:
			case 111:
			case 112:
			case 120:
			case 121:
			case 122:
			case 130:
			case 131:
			case 132:
				maxhp += rand(300, 350);
				break;
			case 210:
			case 211:
			case 212:
			case 220:
			case 221:
			case 222:
			case 230:
			case 231:
			case 232:
				maxmp += rand(450, 500);
				break;
			case 310:
			case 311:
			case 312:
			case 320:
			case 321:
			case 322:
			case 410:
			case 411:
			case 412:
			case 420:
			case 421:
			case 422:
			case 510:
			case 511:
			case 512:
			case 520:
			case 521:
			case 522:
				maxhp += rand(300, 350);
				maxmp += rand(150, 200);
				break;
			default:
				break;
		}
		if (maxhp >= 30000) {
			maxhp = 30000;
		}
		if (maxmp >= 30000) {
			maxmp = 30000;
		}
		setHp(maxhp);
		setMp(maxmp);
		List<Pair<MapleStat, Integer>> statup = new ArrayList<Pair<MapleStat, Integer>>(2);
		statup.add(new Pair<MapleStat, Integer>(MapleStat.MAXHP, Integer.valueOf(maxhp)));
		statup.add(new Pair<MapleStat, Integer>(MapleStat.MAXMP, Integer.valueOf(maxmp)));
		recalcLocalStats();
		getClient().getSession().write(MaplePacketCreator.updatePlayerStats(statup));
		getMap().broadcastMessage(this, MaplePacketCreator.showJobChange(getId()), false);
		silentPartyUpdate();
		guildUpdate();
	}

	public void gainAp(int ap) {
		this.remainingAp += ap;
		updateSingleStat(MapleStat.AVAILABLEAP, this.remainingAp);
	}

	public void changeSkillLevel(ISkill skill, int newLevel, int newMasterlevel) {
		skills.put(skill, new SkillEntry(newLevel, newMasterlevel));
		this.getClient().getSession().write(MaplePacketCreator.updateSkill(skill.getId(), newLevel, newMasterlevel));
	}

	public void setHp(int newhp) {
		setHp(newhp, false);
	}

	public void setHp(int newhp, boolean silent) {
		int oldHp = hp;
		int thp = newhp;
		if (thp < 0) {
			thp = 0;
		}
		if (thp > localmaxhp) {
			thp = localmaxhp;
		}
		this.hp = thp;

		if (!silent) {
			updatePartyMemberHP();
		}
		if (oldHp > hp && !isAlive()) {
			playerDead();
		}
	}

	private void playerDead() {
		if (getEventInstance() != null) {
			getEventInstance().playerKilled(this);
		}
		dispelSkill(0);
		dispelDebuffs();
		cancelMorphs();
		cancelBeholder();

		int[] charmID = {5130000, 4031283, 4140903}; //NOTE Also checks in this order
		MapleCharacter player = getClient().getPlayer();
		int possesed = 0;
		int i;

		//Check for charms
		for (i = 0; i < charmID.length; i++) {
			int quantity = getItemQuantity(charmID[i], false);
			if (possesed == 0 && quantity > 0) {
				possesed = quantity;
				break;
			}
		}

		if (possesed > 0) {
			//Our player got lucky this time!
			possesed -= 1;
			getClient().getSession().write(MaplePacketCreator.serverNotice(5, "You have used the safety charm once, so your EXP points have not been decreased. (" + possesed + "time(s) left)"));
			MapleInventoryManipulator.removeById(getClient(), MapleItemInformationProvider.getInstance().getInventoryType(charmID[i]), charmID[i], 1, true, false);
		} else {
			if (player.getJob() != MapleJob.BEGINNER) {
				//Lose XP
				int XPdummy = ExpTable.getExpNeededForLevel(player.getLevel() + 1);
				if (player.getMap().isTown()) {
					XPdummy *= 0.01;
				}

				if (XPdummy == ExpTable.getExpNeededForLevel(player.getLevel() + 1)) {
					//Thank you LaiLaiNoob for the information
					if (player.getLuk() <= 100 && player.getLuk() > 8) {
						XPdummy *= 0.10 - (player.getLuk() * 0.0005);
					} else if (player.getLuk() < 8) {
						XPdummy *= 0.10; //Otherwise they lose about 9 percent
					} else {
						XPdummy *= 0.10 - (100 * 0.0005);
					}
				}

				if ((player.getExp() - XPdummy) > 0) {
					player.gainExp(-XPdummy, false, false);
				} else {
					player.gainExp(-player.getExp(), false, false);
				}
			}
		}

		getClient().getSession().write(MaplePacketCreator.enableActions());
	}

	public void updatePartyMemberHP() {
		if (party != null) {
			int channel = client.getChannel();
			for (MaplePartyCharacter partychar : party.getMembers()) {
				if (partychar.getMapid() == getMapId() && partychar.getChannel() == channel) {
					MapleCharacter other = ChannelServer.getInstance(channel).getPlayerStorage().getCharacterByName(partychar.getName());
					if (other != null) {
						other.getClient().getSession().write(MaplePacketCreator.updatePartyMemberHP(getId(), this.hp, localmaxhp));
					}
				}
			}
		}
	}

	public void receivePartyMemberHP() {
		if (party != null) {
			int channel = client.getChannel();
			for (MaplePartyCharacter partychar : party.getMembers()) {
				if (partychar.getMapid() == getMapId() && partychar.getChannel() == channel) {
					MapleCharacter other = ChannelServer.getInstance(channel).getPlayerStorage().getCharacterByName(partychar.getName());
					if (other != null) {
						getClient().getSession().write(
							MaplePacketCreator.updatePartyMemberHP(other.getId(), other.getHp(), other.getCurrentMaxHp()));
					}
				}
			}
		}
	}

	public void setMp(int newmp) {
		int tmp = newmp;
		if (tmp < 0) {
			tmp = 0;
		}
		if (tmp > localmaxmp) {
			tmp = localmaxmp;
		}
		this.mp = tmp;
	}

	/**
	 * Convenience function which adds the supplied parameter to the current hp then directly does a updateSingleStat.
	 *
	 * @see MapleCharacter#setHp(int)
	 * @param delta
	 */
	public void addHP(int delta) {
		setHp(hp + delta);
		updateSingleStat(MapleStat.HP, hp);
	}

	/**
	 * Convenience function which adds the supplied parameter to the current mp then directly does a updateSingleStat.
	 *
	 * @see MapleCharacter#setMp(int)
	 * @param delta
	 */
	public void addMP(int delta) {
		setMp(mp + delta);
		updateSingleStat(MapleStat.MP, mp);
	}

	public void addMPHP(int hpDiff, int mpDiff) {
		setHp(hp + hpDiff);
		setMp(mp + mpDiff);
		List<Pair<MapleStat, Integer>> stats = new ArrayList<Pair<MapleStat, Integer>>();
		stats.add(new Pair<MapleStat, Integer>(MapleStat.HP, Integer.valueOf(hp)));
		stats.add(new Pair<MapleStat, Integer>(MapleStat.MP, Integer.valueOf(mp)));
		MaplePacket updatePacket = MaplePacketCreator.updatePlayerStats(stats);
		client.getSession().write(updatePacket);
	}

	/**
	 * Updates a single stat of this MapleCharacter for the client. This method only creates and sends an update packet,
	 * it does not update the stat stored in this MapleCharacter instance.
	 *
	 * @param stat
	 * @param newval
	 * @param itemReaction
	 */
	public void updateSingleStat(MapleStat stat, int newval, boolean itemReaction) {
		Pair<MapleStat, Integer> statpair = new Pair<MapleStat, Integer>(stat, Integer.valueOf(newval));
		MaplePacket updatePacket = MaplePacketCreator.updatePlayerStats(Collections.singletonList(statpair), itemReaction);
		client.getSession().write(updatePacket);
	}

	public void updateSingleStat(MapleStat stat, int newval) {
		updateSingleStat(stat, newval, false);
	}

	public void gainExp(int gain, boolean show, boolean inChat, boolean white) {
		int gainShow = gain;
		if (getLevel() < 200) { // lvl 200 is max and has 0 exp required to level
			if ((long) this.exp.get() + (long) gain > (long) Integer.MAX_VALUE) {
				int gainFirst = ExpTable.getExpNeededForLevel(level + 1) - this.exp.get();
				gain -= gainFirst + 1;
				this.gainExp(gainFirst + 1, false, inChat, white);
			}
			int newexp = this.exp.addAndGet(gain);
			updateSingleStat(MapleStat.EXP, newexp);
		} else {
			this.exp.set(0);
			updateSingleStat(MapleStat.EXP, 0);
		}
		if (show && gain != 0) { // still show the exp gain even if it's not there
			client.getSession().write(MaplePacketCreator.getShowExpGain(gainShow, inChat, white));
		}
        if (level < 200 && exp.get() >= ExpTable.getExpNeededForLevel(level + 1)) {
            levelUp();
            if (level < 200) {
                int update = 0;
                if (this.exp.get() >= ExpTable.getExpNeededForLevel(level + 1)) {
                    update = ExpTable.getExpNeededForLevel(level + 1) - 1;
                } else {
                    update = this.exp.get();
                }
                this.exp.set(update);
                updateSingleStat(MapleStat.EXP, update);
            }
        }
    }

	public void silentPartyUpdate() {
		if (party != null) {
			try {
				getClient().getChannelServer().getWorldInterface().updateParty(party.getId(),
					PartyOperation.SILENT_UPDATE, new MaplePartyCharacter(MapleCharacter.this));
			} catch (RemoteException e) {
				log.error("REMOTE THROW", e);
				getClient().getChannelServer().reconnectWorld();
			}
		}
	}

	public void gainExp(int gain, boolean show, boolean inChat) {
		gainExp(gain, show, inChat, true);
	}

	public boolean isGM() {
		return gmLevel > 0;
	}

	public int getGMLevel() {
		return gmLevel;
	}

	public boolean hasGmLevel(int level) {
		return gmLevel >= level;
	}

	public MapleInventory getInventory(MapleInventoryType type) {
		return inventory[type.ordinal()];
	}

	public MapleShop getShop() {
		return shop;
	}

	public void setShop(MapleShop shop) {
		this.shop = shop;
	}

	public int getMeso() {
		return meso.get();
	}

	public int getSavedLocation(SavedLocationType type) {
		return savedLocations[type.ordinal()];
	}

	public void saveLocation(SavedLocationType type) {
		savedLocations[type.ordinal()] = getMapId();
	}

	public void clearSavedLocation(SavedLocationType type) {
		savedLocations[type.ordinal()] = -1;
	}

	public void gainMeso(int gain, boolean show) {
		gainMeso(gain, show, false, false);
	}

	public void gainMeso(int gain, boolean show, boolean enableActions) {
		gainMeso(gain, show, enableActions, false);
	}

	public void gainMeso(int gain, boolean show, boolean enableActions, boolean inChat) {
		if (meso.get() + gain < 0) {
			client.getSession().write(MaplePacketCreator.enableActions());
			return;
		}
		int newVal = meso.addAndGet(gain);
		updateSingleStat(MapleStat.MESO, newVal, enableActions);
		if (show) {
			client.getSession().write(MaplePacketCreator.getShowMesoGain(gain, inChat));
		}
	}

	/**
	 * Adds this monster to the controlled list. The monster must exist on the Map.
	 *
	 * @param monster
	 */
	public void controlMonster(MapleMonster monster, boolean aggro) {
		monster.setController(this);
		controlled.add(monster);
		client.getSession().write(MaplePacketCreator.controlMonster(monster, false, aggro));
	}

	public void stopControllingMonster(MapleMonster monster) {
		controlled.remove(monster);
	}

	public void checkMonsterAggro(MapleMonster monster) {
		if (!monster.isControllerHasAggro()) {
			if (monster.getController() == this) {
				monster.setControllerHasAggro(true);
			} else {
				monster.switchController(this, true);
			}
		}
	}

	public Collection<MapleMonster> getControlledMonsters() {
		return Collections.unmodifiableCollection(controlled);
	}

	public int getNumControlledMonsters() {
		return controlled.size();
	}

	@Override
	public String toString() {
		return "Character: " + this.name;
	}

	public int getAccountID() {
		return accountid;
	}

	public void mobKilled(int id) {
		for (MapleQuestStatus q : quests.values()) {
			if (q.getStatus() == MapleQuestStatus.Status.COMPLETED || q.getQuest().canComplete(this, null)) {
				continue;
			}
			if (q.mobKilled(id) && !(q.getQuest() instanceof MapleCustomQuest)) {
				client.getSession().write(MaplePacketCreator.updateQuestMobKills(q));
				if (q.getQuest().canComplete(this, null)) {
					client.getSession().write(MaplePacketCreator.getShowQuestCompletion(q.getQuest().getId()));
				}
			}
		}
	}

	public final List<MapleQuestStatus> getStartedQuests() {
		List<MapleQuestStatus> ret = new LinkedList<MapleQuestStatus>();
		for (MapleQuestStatus q : quests.values()) {
			if (q.getStatus().equals(MapleQuestStatus.Status.STARTED) && !(q.getQuest() instanceof MapleCustomQuest)) {
				ret.add(q);
			}
		}
		return Collections.unmodifiableList(ret);
	}

	public final List<MapleQuestStatus> getCompletedQuests() {
		List<MapleQuestStatus> ret = new LinkedList<MapleQuestStatus>();
		for (MapleQuestStatus q : quests.values()) {
			if (q.getStatus().equals(MapleQuestStatus.Status.COMPLETED) && !(q.getQuest() instanceof MapleCustomQuest)) {
				ret.add(q);
			}
		}
		return Collections.unmodifiableList(ret);
	}

	public MaplePlayerShop getPlayerShop() {
		return playerShop;
	}

	public void setPlayerShop(MaplePlayerShop playerShop) {
		this.playerShop = playerShop;
	}

	public Map<ISkill, SkillEntry> getSkills() {
		return Collections.unmodifiableMap(skills);
	}

	public void dispelSkill(int skillid) {
		LinkedList<MapleBuffStatValueHolder> allBuffs = new LinkedList<MapleBuffStatValueHolder>(effects.values());
		for (MapleBuffStatValueHolder mbsvh : allBuffs) {
			if (skillid == 0) {
				if (mbsvh.effect.isSkill()) {
					switch (mbsvh.effect.getSourceId()) {
						case 1004:
						case 1321007:
						case 2121005:
						case 2221005:
						case 2311006:
						case 2321003:
						case 3111002:
						case 3111005:
						case 3211002:
						case 3211005:
						case 4111002:
							cancelEffect(mbsvh.effect, false, mbsvh.startTime);

					}
				}
			} else {
				if (mbsvh.effect.isSkill() && mbsvh.effect.getSourceId() == skillid) {
					cancelEffect(mbsvh.effect, false, mbsvh.startTime);
				}
			}
		}
	}

	public int getSkillLevel(ISkill skill) {
		SkillEntry ret = skills.get(skill);
		if (ret == null) {
			return 0;
		}
		return ret.skillevel;
	}

	public int getMasterLevel(ISkill skill) {
		SkillEntry ret = skills.get(skill);
		if (ret == null) {
			return 0;
		}
		return ret.masterlevel;
	}

	// the equipped inventory only contains equip... I hope
	public int getTotalDex() {
		return localdex;
	}

	public int getTotalInt() {
		return localint_;
	}

	public int getTotalStr() {
		return localstr;
	}

	public int getTotalLuk() {
		return localluk;
	}

	public int getTotalMagic() {
		return magic;
	}

	public double getSpeedMod() {
		return speedMod;
	}

	public double getJumpMod() {
		return jumpMod;
	}

	public int getTotalWatk() {
		return watk;
	}

	private static int rand(int lbound, int ubound) {
		return (int) ((Math.random() * (ubound - lbound + 1)) + lbound);
	}

	public void levelUp() {
		ISkill improvingMaxHP = null;
		int improvingMaxHPLevel = 0;
		ISkill improvingMaxMP = SkillFactory.getSkill(2000001);
		int improvingMaxMPLevel = getSkillLevel(improvingMaxMP);
		remainingAp += 5;
		if (job == MapleJob.BEGINNER) {
			maxhp += rand(12, 16);
			maxmp += rand(10, 12);
		} else if (job.isA(MapleJob.WARRIOR)) {
			improvingMaxHP = SkillFactory.getSkill(1000001);
			improvingMaxHPLevel = getSkillLevel(improvingMaxHP);
			maxhp += rand(24, 28);
			maxmp += rand(4, 6);
		} else if (job.isA(MapleJob.MAGICIAN)) {
			maxhp += rand(10, 14);
			maxmp += rand(22, 24);
		} else if (job.isA(MapleJob.BOWMAN) || job.isA(MapleJob.THIEF) || job.isA(MapleJob.GM)) {
			maxhp += rand(20, 24);
			maxmp += rand(14, 16);
		} else if (job.isA(MapleJob.PIRATE)) {
			improvingMaxHP = SkillFactory.getSkill(5100000);
			improvingMaxHPLevel = getSkillLevel(improvingMaxHP);
			maxhp += rand(22, 28);
			maxmp += rand(18, 23);
		}
		if (improvingMaxHPLevel > 0) {
			maxhp += improvingMaxHP.getEffect(improvingMaxHPLevel).getX();
		}
		if (improvingMaxMPLevel > 0) {
			maxmp += improvingMaxMP.getEffect(improvingMaxMPLevel).getX();
		}
		maxmp += getTotalInt() / 10;
		exp.addAndGet(-ExpTable.getExpNeededForLevel(level + 1));
		level += 1;
		if (level == 200 && !isGM()) {
			exp.set(0);
			MaplePacket packet = MaplePacketCreator.serverNotice(6, "[Congrats] " + getName() + " has reached Level 200! Congratulate " + getName() + " on such an amazing achievement!");
			try {
				getClient().getChannelServer().getWorldInterface().broadcastMessage(getName(), packet.getBytes());
			} catch (RemoteException e) {
				getClient().getChannelServer().reconnectWorld();
			}
		}

		maxhp = Math.min(30000, maxhp);
		maxmp = Math.min(30000, maxmp);

		List<Pair<MapleStat, Integer>> statup = new ArrayList<Pair<MapleStat, Integer>>(8);
		statup.add(new Pair<MapleStat, Integer>(MapleStat.AVAILABLEAP, Integer.valueOf(remainingAp)));
		statup.add(new Pair<MapleStat, Integer>(MapleStat.MAXHP, Integer.valueOf(maxhp)));
		statup.add(new Pair<MapleStat, Integer>(MapleStat.MAXMP, Integer.valueOf(maxmp)));
		statup.add(new Pair<MapleStat, Integer>(MapleStat.HP, Integer.valueOf(maxhp)));
		statup.add(new Pair<MapleStat, Integer>(MapleStat.MP, Integer.valueOf(maxmp)));
		statup.add(new Pair<MapleStat, Integer>(MapleStat.EXP, Integer.valueOf(exp.get())));
		statup.add(new Pair<MapleStat, Integer>(MapleStat.LEVEL, Integer.valueOf(level)));

		if (job != MapleJob.BEGINNER) {
			remainingSp += 3;
			statup.add(new Pair<MapleStat, Integer>(MapleStat.AVAILABLESP, Integer.valueOf(remainingSp)));
		}

		setHp(maxhp);
		setMp(maxmp);
		getClient().getSession().write(MaplePacketCreator.updatePlayerStats(statup));
		getMap().broadcastMessage(this, MaplePacketCreator.showLevelup(getId()), false);
		recalcLocalStats();
		silentPartyUpdate();
		guildUpdate();
		saveToDB(true);
	}

	public void changeKeybinding(int key, MapleKeyBinding keybinding) {
		if (keybinding.getType() != 0) {
			keymap.put(Integer.valueOf(key), keybinding);
		} else {
			keymap.remove(Integer.valueOf(key));
		}
	}

	public void sendKeymap() {
		getClient().getSession().write(MaplePacketCreator.getKeymap(keymap));
	}

	public void sendMacros() {
		boolean macros = false;
		for (int i = 0; i < 5; i++) {
			if (skillMacros[i] != null) {
				macros = true;
			}
		}
		if (macros) {
			getClient().getSession().write(MaplePacketCreator.getMacros(skillMacros));
		}
	}

	public void updateMacros(int position, SkillMacro updateMacro) {
		skillMacros[position] = updateMacro;
	}

	public void tempban(String reason, Calendar duration, int greason, boolean IPMac) {
		if (lastmonthfameids == null) {
			throw new RuntimeException("Trying to ban a non-loaded character (testhack)");
		}
        try {
            if (IPMac)
                getClient().banMacs();
                Connection con = DatabaseConnection.getConnection();
                PreparedStatement ps = con.prepareStatement("INSERT INTO ipbans VALUES (DEFAULT, ?)");
                String[] ipSplit = client.getSession().getRemoteAddress().toString().split(":");
                ps.setString(1, ipSplit[0]);
                ps.executeUpdate();
                ps.close();
        } catch (SQLException ex) {
                log.error("Error while tempbanning", ex);
        }
		tempban(reason, duration, greason, client.getAccID());
		client.getSession().close();
	}

	public static boolean tempban(String reason, Calendar duration, int greason, int accountid) {
		try {
			Connection con = DatabaseConnection.getConnection();
			PreparedStatement ps = con.prepareStatement("UPDATE accounts SET tempban = ?, banreason = ?, greason = ? WHERE id = ?");
			Timestamp TS = new Timestamp(duration.getTimeInMillis());
			ps.setTimestamp(1, TS);
			ps.setString(2, reason);
			ps.setInt(3, greason);
			ps.setInt(4, accountid);
			ps.executeUpdate();
			ps.close();
			return true;
		} catch (SQLException ex) {
			log.error("Error while tempbanning", ex);
		}
		return false;
	}

	public void ban(String reason, boolean IPMac) {
		if (lastmonthfameids == null) {
			throw new RuntimeException("Trying to ban a non-loaded character (testhack)");
		}
		try {
			Connection con = DatabaseConnection.getConnection();
			PreparedStatement ps = con.prepareStatement("UPDATE accounts SET banned = ?, banreason = ? WHERE id = ?");
			ps.setInt(1, 1);
			ps.setString(2, reason);
			ps.setInt(3, accountid);
			ps.executeUpdate();
			ps.close();
            if (IPMac) {
                getClient().banMacs();
                ps = con.prepareStatement("INSERT INTO ipbans VALUES (DEFAULT, ?)");
                String[] ipSplit = client.getSession().getRemoteAddress().toString().split(":");
                ps.setString(1, ipSplit[0]);
                ps.executeUpdate();
                ps.close();
            } 
		} catch (SQLException ex) {
			log.error("Error while banning", ex);
		}
		client.getSession().close();
	}

	public static boolean ban(String id, String reason, boolean accountId) {
		try {
			Connection con = DatabaseConnection.getConnection();
			PreparedStatement ps;
			if (id.matches("/[0-9]{1,3}\\..*")) {
				ps = con.prepareStatement("INSERT INTO ipbans VALUES (DEFAULT, ?)");
				ps.setString(1, id);
				ps.executeUpdate();
				ps.close();
				return true;
			}
			if (accountId) {
				ps = con.prepareStatement("SELECT id FROM accounts WHERE name = ?");
			} else {
				ps = con.prepareStatement("SELECT accountid FROM characters WHERE name = ?");
			}
			boolean ret = false;
			ps.setString(1, id);
			ResultSet rs = ps.executeQuery();
			if (rs.next()) {
				PreparedStatement psb = con.prepareStatement("UPDATE accounts SET banned = 1, banreason = ? WHERE id = ?");
				psb.setString(1, reason);
				psb.setInt(2, rs.getInt(1));
				psb.executeUpdate();
				psb.close();
				ret = true;
			}
			rs.close();
			ps.close();
			return ret;
		} catch (SQLException ex) {
			log.error("Error while banning", ex);
		}
		return false;
	}

	/**
	 * Oid of players is always = the cid
	 */
	@Override
	public int getObjectId() {
		return getId();
	}

	/**
	 * Throws unsupported operation exception, oid of players is read only
	 */
	@Override
	public void setObjectId(int id) {
		throw new UnsupportedOperationException();
	}

	public MapleStorage getStorage() {
		return storage;
	}

	public int getCurrentMaxHp() {
		return localmaxhp;
	}

	public int getCurrentMaxMp() {
		return localmaxmp;
	}

	public int getCurrentMaxBaseDamage() {
		return localmaxbasedamage;
	}

	public int calculateMaxBaseDamage(int watk) {
		int maxbasedamage;
		if (watk == 0) {
			maxbasedamage = 1;
		} else {
			IItem weapon_item = getInventory(MapleInventoryType.EQUIPPED).getItem((byte) -11);
			if (weapon_item != null) {
				MapleWeaponType weapon = MapleItemInformationProvider.getInstance().getWeaponType(weapon_item.getItemId());
				int mainstat;
				int secondarystat;
				if (weapon == MapleWeaponType.BOW || weapon == MapleWeaponType.CROSSBOW) {
					mainstat = localdex;
					secondarystat = localstr;
				} else if (getJob().isA(MapleJob.THIEF) && (weapon == MapleWeaponType.CLAW || weapon == MapleWeaponType.DAGGER)) {
					mainstat = localluk;
					secondarystat = localdex + localstr;
				} else {
					mainstat = localstr;
					secondarystat = localdex;
				}
				maxbasedamage = (int) (((weapon.getMaxDamageMultiplier() * mainstat + secondarystat) / 100.0) * watk);
				//just some saveguard against rounding errors, we want to a/b for this
				maxbasedamage += 10;
			} else {
				maxbasedamage = 0;
			}
		}
		return maxbasedamage;
	}

	public void addVisibleMapObject(MapleMapObject mo) {
		visibleMapObjects.add(mo);
	}

	public void removeVisibleMapObject(MapleMapObject mo) {
		visibleMapObjects.remove(mo);
	}

	public boolean isMapObjectVisible(MapleMapObject mo) {
		return visibleMapObjects.contains(mo);
	}

	public Collection<MapleMapObject> getVisibleMapObjects() {
		return Collections.unmodifiableCollection(visibleMapObjects);
	}

	public boolean isAlive() {
		return this.hp > 0;
	}

	public void setSlot(int slotid) {
		slots = slotid;
	}

	public int getSlot() {
		return slots;
	}

	@Override
	public void sendDestroyData(MapleClient client) {
		client.getSession().write(MaplePacketCreator.removePlayerFromMap(this.getObjectId()));
	}

	@Override
	public void sendSpawnData(MapleClient client) {
		if (!this.isHidden()) {
			client.getSession().write(MaplePacketCreator.spawnPlayerMapobject(this));
			for (int i = 0; i < 3; i++) {
				if (pets[i] != null) {
					client.getSession().write(MaplePacketCreator.showPet(this, pets[i], false, false));
				}
			}
		}
	}

	private void recalcLocalStats() {
		int oldmaxhp = localmaxhp;
		localmaxhp = getMaxHp();
		localmaxmp = getMaxMp();
		localdex = getDex();
		localint_ = getInt();
		localstr = getStr();
		localluk = getLuk();
		int speed = 100;
		int jump = 100;
		magic = localint_;
		watk = 0;
		for (IItem item : getInventory(MapleInventoryType.EQUIPPED)) {
			IEquip equip = (IEquip) item;
			localmaxhp += equip.getHp();
			localmaxmp += equip.getMp();
			localdex += equip.getDex();
			localint_ += equip.getInt();
			localstr += equip.getStr();
			localluk += equip.getLuk();
			magic += equip.getMatk() + equip.getInt();
			watk += equip.getWatk();
			speed += equip.getSpeed();
			jump += equip.getJump();
		}
		magic = Math.min(magic, 2000);
		Integer hbhp = getBuffedValue(MapleBuffStat.HYPERBODYHP);
		if (hbhp != null) {
			localmaxhp += (hbhp.doubleValue() / 100) * localmaxhp;
		}
		Integer hbmp = getBuffedValue(MapleBuffStat.HYPERBODYMP);
		if (hbmp != null) {
			localmaxmp += (hbmp.doubleValue() / 100) * localmaxmp;
		}
		localmaxhp = Math.min(30000, localmaxhp);
		localmaxmp = Math.min(30000, localmaxmp);
		Integer watkbuff = getBuffedValue(MapleBuffStat.WATK);
		if (watkbuff != null) {
			watk += watkbuff.intValue();
		}
		if (job.isA(MapleJob.BOWMAN)) {
			ISkill expert = null;
			if (job.isA(MapleJob.CROSSBOWMASTER)) {
				expert = SkillFactory.getSkill(3220004);
			} else if (job.isA(MapleJob.BOWMASTER)) {
				expert = SkillFactory.getSkill(3120005);
			}
			if (expert != null) {
				int boostLevel = getSkillLevel(expert);
				if (boostLevel > 0) {
					watk += expert.getEffect(boostLevel).getX();
				}
			}
		}
		Integer matkbuff = getBuffedValue(MapleBuffStat.MATK);
		if (matkbuff != null) {
			magic += matkbuff.intValue();
		}
		Integer speedbuff = getBuffedValue(MapleBuffStat.SPEED);
		if (speedbuff != null) {
			speed += speedbuff.intValue();
		}
		Integer jumpbuff = getBuffedValue(MapleBuffStat.JUMP);
		if (jumpbuff != null) {
			jump += jumpbuff.intValue();
		}
		if (speed > 140) {
			speed = 140;
		}
		if (jump > 123) {
			jump = 123;
		}
		speedMod = speed / 100.0;
		jumpMod = jump / 100.0;
		Integer mount = getBuffedValue(MapleBuffStat.MONSTER_RIDING);
		if (mount != null) {
			jumpMod = 1.23;
			switch (mount.intValue()) {
				case 1:
					speedMod = 1.5;
					break;
				case 2:
					speedMod = 1.7;
					break;
				case 3:
					speedMod = 1.8;
					break;
				default:
					log.warn("Unhandeled monster riding level");
			}
		}
		localmaxbasedamage = calculateMaxBaseDamage(watk);
		if (oldmaxhp != 0 && oldmaxhp != localmaxhp) {
			updatePartyMemberHP();
		}
	}

	public void equipChanged() {
		getMap().broadcastMessage(this, MaplePacketCreator.updateCharLook(this), false);
		recalcLocalStats();
		enforceMaxHpMp();
		if (getClient().getPlayer().getMessenger() != null) {
			WorldChannelInterface wci = ChannelServer.getInstance(getClient().getChannel()).getWorldInterface();
			try {
				wci.updateMessenger(getClient().getPlayer().getMessenger().getId(), getClient().getPlayer().getName(), getClient().getChannel());
			} catch (RemoteException e) {
				getClient().getChannelServer().reconnectWorld();
			}
		}
	}

	public MaplePet getPet(int index) {
		return pets[index];
	}

	public void addPet(MaplePet pet) {
		for (int i = 0; i < 3; i++) {
			if (pets[i] == null) {
				pets[i] = pet;
				return;
			}
		}
	}

	public void removePet(MaplePet pet, boolean shift_left) {
		int slot = -1;
		for (int i = 0; i < 3; i++) {
			if (pets[i] != null) {
				if (pets[i].getUniqueId() == pet.getUniqueId()) {
					pets[i] = null;
					slot = i;
					break;
				}
			}
		}
		if (shift_left) {
			if (slot > -1) {
				for (int i = slot; i < 3; i++) {
					if (i != 2) {
						pets[i] = pets[i + 1];
					} else {
						pets[i] = null;
					}
				}
			}
		}
	}

	public int getNoPets() {
		int ret = 0;
		for (int i = 0; i < 3; i++) {
			if (pets[i] != null) {
				ret++;
			}
		}
		return ret;
	}

	public int getPetIndex(MaplePet pet) {
		for (int i = 0; i < 3; i++) {
			if (pets[i] != null) {
				if (pets[i].getUniqueId() == pet.getUniqueId()) {
					return i;
				}
			}
		}
		return -1;
	}

	public int getPetIndex(int petId) {
		for (int i = 0; i < 3; i++) {
			if (pets[i] != null) {
				if (pets[i].getUniqueId() == petId) {
					return i;
				}
			}
		}
		return -1;
	}

	public int getNextEmptyPetIndex() {
		if (pets[0] == null) {
			return 0;
		}
		if (pets[1] == null) {
			return 1;
		}
		if (pets[2] == null) {
			return 2;
		}
		return 3;
	}

	public MaplePet[] getPets() {
		return pets;
	}

	public void unequipAllPets() {
		for (int i = 0; i < 3; i++) {
			if (pets[i] != null) {
				unequipPet(pets[i], true);
			}
		}
	}

	public void unequipPet(MaplePet pet, boolean shift_left) {
		unequipPet(pet, shift_left, false);
	}

	public void unequipPet(MaplePet pet, boolean shift_left, boolean hunger) {
		cancelFullnessSchedule(getPetIndex(pet));

		pet.saveToDb();

		// Broadcast the packet to the map - with null instead of MaplePet
		getMap().broadcastMessage(this, MaplePacketCreator.showPet(this, pet, true, hunger), true);

		// Make a new list for the stat updates
		List<Pair<MapleStat, Integer>> stats = new ArrayList<Pair<MapleStat, Integer>>();
		stats.add(new Pair<MapleStat, Integer>(MapleStat.PET, Integer.valueOf(0)));

		// Write the stat update to the player...
		getClient().getSession().write(MaplePacketCreator.petStatUpdate(this));
		getClient().getSession().write(MaplePacketCreator.enableActions());

		// Un-assign the pet set to the player
		removePet(pet, shift_left);
	}

	public void shiftPetsRight() {
		if (pets[2] == null) {
			pets[2] = pets[1];
			pets[1] = pets[0];
			pets[0] = null;
		}
	}

	public FameStatus canGiveFame(MapleCharacter from) {
		if (lastfametime >= System.currentTimeMillis() - 60 * 60 * 24 * 1000) {
			return FameStatus.NOT_TODAY;
		} else if (lastmonthfameids.contains(Integer.valueOf(from.getId()))) {
			return FameStatus.NOT_THIS_MONTH;
		} else {
			return FameStatus.OK;
		}
	}

	public void hasGivenFame(MapleCharacter to) {
		lastfametime = System.currentTimeMillis();
		lastmonthfameids.add(Integer.valueOf(to.getId()));
		Connection con = DatabaseConnection.getConnection();
		try {
			PreparedStatement ps = con.prepareStatement("INSERT INTO famelog (characterid, characterid_to) VALUES (?, ?)");
			ps.setInt(1, getId());
			ps.setInt(2, to.getId());
			ps.executeUpdate();
			ps.close();
		} catch (SQLException e) {
			log.error("ERROR writing famelog for char " + getName() + " to " + to.getName(), e);
		}
	}

	public MapleParty getParty() {
		return party;
	}

	public int getPartyId() {
		return (party != null ? party.getId() : -1);
	}

	public int getWorld() {
		return world;
	}

	public void setWorld(int world) {
		this.world = world;
	}

	public void setParty(MapleParty party) {
		this.party = party;
	}

	public MapleTrade getTrade() {
		return trade;
	}

	public void setTrade(MapleTrade trade) {
		this.trade = trade;
	}

	public EventInstanceManager getEventInstance() {
		return eventInstance;
	}

	public void setEventInstance(EventInstanceManager eventInstance) {
		this.eventInstance = eventInstance;
	}

	public void addDoor(MapleDoor door) {
		doors.add(door);
	}

	public void clearDoors() {
		doors.clear();
	}

	public List<MapleDoor> getDoors() {
		return new ArrayList<MapleDoor>(doors);
	}

	public boolean canDoor() {
		return canDoor;
	}

	public void disableDoor() {
		canDoor = false;
		TimerManager tMan = TimerManager.getInstance();
		tMan.schedule(new Runnable() {

			@Override
			public void run() {
				canDoor = true;
			}
		}, 5000);
	}

	public Map<Integer, MapleSummon> getSummons() {
		return summons;
	}

	public int getChair() {
		return chair;
	}

	public int getItemEffect() {
		return itemEffect;
	}

	public void setChair(int chair) {
		this.chair = chair;
	}

	public void setItemEffect(int itemEffect) {
		this.itemEffect = itemEffect;
	}

	@Override
	public Collection<MapleInventory> allInventories() {
		return Arrays.asList(inventory);
	}

	@Override
	public MapleMapObjectType getType() {
		return MapleMapObjectType.PLAYER;
	}

	public int getGuildId() {
		return guildid;
	}

	public int getGuildRank() {
		return guildrank;
	}

	public void setGuildId(int _id) {
		guildid = _id;
		if (guildid > 0) {
			if (mgc == null) {
				mgc = new MapleGuildCharacter(this);
			} else {
				mgc.setGuildId(guildid);
			}
		} else {
			mgc = null;
		}
	}

	public void setGuildRank(int _rank) {
		guildrank = _rank;
		if (mgc != null) {
			mgc.setGuildRank(_rank);
		}
	}

	public MapleGuildCharacter getMGC() {
		return mgc;
	}

	public void guildUpdate() {
		if (this.guildid <= 0) {
			return;
		}

		mgc.setLevel(this.level);
		mgc.setJobId(this.job.getId());

		try {
			this.client.getChannelServer().getWorldInterface().memberLevelJobUpdate(this.mgc);
		} catch (RemoteException re) {
			log.error("RemoteExcept while trying to update level/job in guild.", re);
		}
	}
	private NumberFormat nf = new DecimalFormat("#,###,###,###");

	public String guildCost() {
		return nf.format(MapleGuild.CREATE_GUILD_COST);
	}

	public String emblemCost() {
		return nf.format(MapleGuild.CHANGE_EMBLEM_COST);
	}

	public String capacityCost() {
		return nf.format(MapleGuild.INCREASE_CAPACITY_COST);
	}

	public void genericGuildMessage(int code) {
		this.client.getSession().write(MaplePacketCreator.genericGuildMessage((byte) code));
	}

	public void disbandGuild() {
		if (guildid <= 0 || guildrank != 1) {
			log.warn(this.name + " tried to disband and s/he is either not in a guild or not leader.");
			return;
		}

		try {
			client.getChannelServer().getWorldInterface().disbandGuild(this.guildid);
		} catch (Exception e) {
			log.error("Error while disbanding guild.", e);
		}
	}

	public void increaseGuildCapacity() {
		if (this.getMeso() < MapleGuild.INCREASE_CAPACITY_COST) {
			client.getSession().write(MaplePacketCreator.serverNotice(1, "You do not have enough mesos."));
			return;
		}

		if (this.guildid <= 0) {
			log.info(this.name + " is trying to increase guild capacity without being in the guild.");
			return;
		}

		try {
			client.getChannelServer().getWorldInterface().increaseGuildCapacity(this.guildid);
		} catch (Exception e) {
			log.error("Error while increasing capacity.", e);
			return;
		}

		this.gainMeso(-MapleGuild.INCREASE_CAPACITY_COST, true, false, true);
	}

	public void saveGuildStatus() {
		Connection con = DatabaseConnection.getConnection();
		try {
			PreparedStatement ps = con.prepareStatement("UPDATE characters SET guildid = ?, guildrank = ? WHERE id = ?");
			ps.setInt(1, this.guildid);
			ps.setInt(2, this.guildrank);
			ps.setInt(3, this.id);
			ps.execute();
			ps.close();
		} catch (SQLException se) {
			log.error("SQL error: " + se.getLocalizedMessage(), se);
		}
	}

	/**
	 * Allows you to change someone's NXCash, Maple Points, and Gift Tokens!
	 *
	 * Created by Acrylic/Penguins
	 *
	 * @param type: 0 = NX, 1 = MP, 2 = GT
	 * @param quantity: how much to modify it by. Negatives subtract points, Positives add points.
	 */
	public void modifyCSPoints(int type, int quantity) {
		switch (type) {
			case 1:
				this.paypalnx += quantity;
				break;
			case 2:
				this.maplepoints += quantity;
				break;
			case 4:
				this.cardnx += quantity;
				break;
		}
	}

	public int getCSPoints(int type) {
		switch (type) {
			case 1:
				return this.paypalnx;
			case 2:
				return this.maplepoints;
			case 4:
				return this.cardnx;
			default:
				return 0;
		}
	}

	public boolean haveItem(int itemid, int quantity, boolean checkEquipped, boolean greaterOrEquals) {
		MapleInventoryType type = MapleItemInformationProvider.getInstance().getInventoryType(itemid);
		MapleInventory iv = inventory[type.ordinal()];
		int possesed = iv.countById(itemid);
		if (checkEquipped) {
			possesed += inventory[MapleInventoryType.EQUIPPED.ordinal()].countById(itemid);
		}
		if (greaterOrEquals) {
			return possesed >= quantity;
		} else {
			return possesed == quantity;
		}
	}

	private static class MapleBuffStatValueHolder {

		public MapleStatEffect effect;
		public long startTime;
		public int value;
		public ScheduledFuture<?> schedule;

		public MapleBuffStatValueHolder(MapleStatEffect effect, long startTime, ScheduledFuture<?> schedule, int value) {
			super();
			this.effect = effect;
			this.startTime = startTime;
			this.schedule = schedule;
			this.value = value;
		}
	}

	public static class MapleCoolDownValueHolder {

		public int skillId;
		public long startTime;
		public long length;
		public ScheduledFuture<?> timer;

		public MapleCoolDownValueHolder(int skillId, long startTime, long length, ScheduledFuture<?> timer) {
			super();
			this.skillId = skillId;
			this.startTime = startTime;
			this.length = length;
			this.timer = timer;
		}
	}

	public static class SkillEntry {

		public int skillevel;
		public int masterlevel;

		public SkillEntry(int skillevel, int masterlevel) {
			this.skillevel = skillevel;
			this.masterlevel = masterlevel;
		}

		@Override
		public String toString() {
			return skillevel + ":" + masterlevel;
		}
	}

	public enum FameStatus {

		OK, NOT_TODAY, NOT_THIS_MONTH
	}

	public int getBuddyCapacity() {
		return buddylist.getCapacity();
	}

	public void setBuddyCapacity(int capacity) {
		buddylist.setCapacity(capacity);
		client.getSession().write(MaplePacketCreator.updateBuddyCapacity(capacity));
	}

	public MapleMessenger getMessenger() {
		return messenger;
	}

	public void setMessenger(MapleMessenger messenger) {
		this.messenger = messenger;
	}

	public void checkMessenger() {
		if (messenger != null && messengerposition < 4 && messengerposition > -1) {
			try {
				WorldChannelInterface wci = ChannelServer.getInstance(client.getChannel()).getWorldInterface();
				MapleMessengerCharacter messengerplayer = new MapleMessengerCharacter(client.getPlayer(), messengerposition);
				wci.silentJoinMessenger(messenger.getId(), messengerplayer, messengerposition);
				wci.updateMessenger(getClient().getPlayer().getMessenger().getId(), getClient().getPlayer().getName(), getClient().getChannel());
			} catch (RemoteException e) {
				client.getChannelServer().reconnectWorld();
			}
		}
	}

	public int getMessengerPosition() {
		return messengerposition;
	}

	public void setMessengerPosition(int position) {
		this.messengerposition = position;
	}

	public int hasEXPCard() {
		return 1;
	}

	public boolean getNXCodeValid(String code, boolean validcode) throws SQLException {

		Connection con = DatabaseConnection.getConnection();
		PreparedStatement ps = con.prepareStatement("SELECT `valid` FROM nxcode WHERE code = ?");
		ps.setString(1, code);
		ResultSet rs = ps.executeQuery();
		while (rs.next()) {
			validcode = rs.getInt("valid") == 0 ? false : true;
		}

		rs.close();
		ps.close();

		return validcode;
	}

	public int getNXCodeType(String code) throws SQLException {

		int type = -1;
		Connection con = DatabaseConnection.getConnection();
		PreparedStatement ps = con.prepareStatement("SELECT `type` FROM nxcode WHERE code = ?");
		ps.setString(1, code);
		ResultSet rs = ps.executeQuery();
		while (rs.next()) {
			type = rs.getInt("type");
		}

		rs.close();
		ps.close();

		return type;
	}

	public int getNXCodeItem(String code) throws SQLException {

		int item = -1;
		Connection con = DatabaseConnection.getConnection();
		PreparedStatement ps = con.prepareStatement("SELECT `item` FROM nxcode WHERE code = ?");
		ps.setString(1, code);
		ResultSet rs = ps.executeQuery();
		while (rs.next()) {
			item = rs.getInt("item");
		}

		rs.close();
		ps.close();

		return item;
	}

	public void setNXCodeUsed(String code) throws SQLException {
		Connection con = DatabaseConnection.getConnection();
		PreparedStatement ps = con.prepareStatement("UPDATE nxcode SET `valid` = 0 WHERE code = ?");
		ps.setString(1, code);
		ps.executeUpdate();
		ps = con.prepareStatement("UPDATE nxcode SET `user` = ? WHERE code = ?");
		ps.setString(1, this.getName());
		ps.setString(2, code);
		ps.executeUpdate();
		ps.close();
	}

	public void setInCS(boolean yesno) {
		this.incs = yesno;
	}

	public boolean inCS() {
		return this.incs;
	}

    public void setInMTS(boolean yesno) {
        this.inmts = yesno;
    }

    public boolean inMTS() {
        return this.inmts;
    }

	public void addCooldown(int skillId, long startTime, long length, ScheduledFuture<?> timer) {
		if (this.coolDowns.containsKey(Integer.valueOf(skillId))) {
			this.coolDowns.remove(skillId);
		}
		this.coolDowns.put(Integer.valueOf(skillId), new MapleCoolDownValueHolder(skillId, startTime, length, timer));
	}

	public void removeCooldown(int skillId) {
		if (this.coolDowns.containsKey(Integer.valueOf(skillId))) {
			this.coolDowns.remove(Integer.valueOf(skillId));
		}
	}

	public boolean skillisCooling(int skillId) {
		return this.coolDowns.containsKey(Integer.valueOf(skillId));
	}

	public void giveCoolDowns(final List<PlayerCoolDownValueHolder> cooldowns) {
		for (PlayerCoolDownValueHolder cooldown : cooldowns) {
			int time = (int) ((cooldown.length + cooldown.startTime) - System.currentTimeMillis());
			ScheduledFuture<?> timer = TimerManager.getInstance().schedule(new CancelCooldownAction(this, cooldown.skillId), time);
			addCooldown(cooldown.skillId, System.currentTimeMillis(), time, timer);
		}
	}

	public List<PlayerCoolDownValueHolder> getAllCooldowns() {
		List<PlayerCoolDownValueHolder> ret = new ArrayList<PlayerCoolDownValueHolder>();
		for (MapleCoolDownValueHolder mcdvh : coolDowns.values()) {
			ret.add(new PlayerCoolDownValueHolder(mcdvh.skillId, mcdvh.startTime, mcdvh.length));
		}
		return ret;
	}

	public static class CancelCooldownAction implements Runnable {

		private int skillId;
		private WeakReference<MapleCharacter> target;

		public CancelCooldownAction(MapleCharacter target, int skillId) {
			this.target = new WeakReference<MapleCharacter>(target);
			this.skillId = skillId;
		}

		@Override
		public void run() {
			MapleCharacter realTarget = target.get();
			if (realTarget != null) {
				realTarget.removeCooldown(skillId);
				realTarget.getClient().getSession().write(MaplePacketCreator.skillCooldown(skillId, 0));
			}
		}
	}

	//should we synch these for the luls?
	public void giveDebuff(MapleDisease disease, MobSkill skill) {
		synchronized (diseases) {
			if (isAlive() && !isActiveBuffedValue(2321005) && !diseases.contains(disease) && diseases.size() < 2) {
				diseases.add(disease);
				List<Pair<MapleDisease, Integer>> debuff = Collections.singletonList(new Pair<MapleDisease, Integer>(disease, Integer.valueOf(skill.getX())));
				long mask = 0;
				for (Pair<MapleDisease, Integer> statup : debuff) {
					mask |= statup.getLeft().getValue();
				}
				getClient().getSession().write(MaplePacketCreator.giveDebuff(mask, debuff, skill));
				getMap().broadcastMessage(this, MaplePacketCreator.giveForeignDebuff(id, mask, skill), false);

				if (isAlive() && diseases.contains(disease)) {
					final MapleCharacter character = this;
					final MapleDisease disease_ = disease;
					TimerManager.getInstance().schedule(new Runnable() {

						@Override
						public void run() {
							if (character.diseases.contains(disease_)) {
								dispelDebuff(disease_);
							}
						}
					}, skill.getDuration());
				}
			}
		}
	}

	public List<MapleDisease> getDiseases() {
		return diseases;
	}

	public void dispelDebuff(MapleDisease debuff) {
		if (diseases.contains(debuff)) {
			diseases.remove(debuff);
			long mask = debuff.getValue();
			getClient().getSession().write(MaplePacketCreator.cancelDebuff(mask));
			getMap().broadcastMessage(this, MaplePacketCreator.cancelForeignDebuff(id, mask), false);
		}
	}

	public void dispelDebuffs() {
        List<MapleDisease> disease_ = new ArrayList<MapleDisease>();
        for (MapleDisease disease : diseases) {
            if (disease == MapleDisease.POISON || disease == MapleDisease.SEAL || disease == MapleDisease.DARKNESS || disease == MapleDisease.WEAKEN || disease == MapleDisease.CURSE) {
                disease_.add(disease);
                getClient().getSession().write(MaplePacketCreator.cancelDebuff(disease_));
                getMap().broadcastMessage(this, MaplePacketCreator.cancelForeignDebuff(this.id, disease_), false);
                disease_.clear();
            }
        }
        this.diseases.clear();
    }

	public void cancelAllDebuffs() {
		for (int i = 0; i < diseases.size(); i++) {
			diseases.remove(i);
			long mask = 0;
			for (MapleDisease statup : diseases) {
				mask |= statup.getValue();
			}
			getClient().getSession().write(MaplePacketCreator.cancelDebuff(mask));
			getMap().broadcastMessage(this, MaplePacketCreator.cancelForeignDebuff(id, mask), false);
		}
	}

	public void setLevel(int level) {
		this.level = level - 1;
	}

	public void setMap(int PmapId) {
		this.mapid = PmapId;
	}

	public List<Integer> getQuestItemsToShow() {
		Set<Integer> delta = new HashSet<Integer>();
		for (Map.Entry<MapleQuest, MapleQuestStatus> questEntry : this.quests.entrySet()) {
			if (questEntry.getValue().getStatus() != MapleQuestStatus.Status.STARTED) {
				delta.addAll(questEntry.getKey().getQuestItemsToShowOnlyIfQuestIsActivated());
			}
		}
		List<Integer> returnThis = new ArrayList<Integer>();
		returnThis.addAll(delta);
		return Collections.unmodifiableList(returnThis);
	}

	public void sendNote(String to, String msg) throws SQLException {
		Connection con = DatabaseConnection.getConnection();
		PreparedStatement ps = con.prepareStatement("INSERT INTO notes (`to`, `from`, `message`, `timestamp`) VALUES (?, ?, ?, ?)");
		ps.setString(1, to);
		ps.setString(2, this.getName());
		ps.setString(3, msg);
		ps.setLong(4, System.currentTimeMillis());
		ps.executeUpdate();
		ps.close();
	}

	public void showNote() throws SQLException {
		Connection con = DatabaseConnection.getConnection();

		PreparedStatement ps = con.prepareStatement("SELECT * FROM notes WHERE `to`=?", ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_UPDATABLE);
		ps.setString(1, this.getName());
		ResultSet rs = ps.executeQuery();

		rs.last();
		int count = rs.getRow();
		rs.first();

		client.getSession().write(MaplePacketCreator.showNotes(rs, count));
		ps.close();
	}

	public void deleteNote(int id) throws SQLException {
		Connection con = DatabaseConnection.getConnection();
		PreparedStatement ps = con.prepareStatement("DELETE FROM notes WHERE `id`=?");
		ps.setInt(1, id);
		ps.executeUpdate();
		ps.close();
	}

	public void checkBerserk() {
		if (BerserkSchedule != null) {
			BerserkSchedule.cancel(false);
		}
		final MapleCharacter chr = this;
		final int skilllevel = getSkillLevel(SkillFactory.getSkill(1320006));
		if (getJob().isA(MapleJob.DARKKNIGHT) && skilllevel > 0) {
			if (chr.getHp() * 100 / chr.getMaxHp() > SkillFactory.getSkill(1320006).getEffect(skilllevel).getX()) {
				Berserk = false;
			} else {
				Berserk = true;
			}
			BerserkSchedule = TimerManager.getInstance().register(new Runnable() {

				@Override
				public void run() {
					getClient().getSession().write(MaplePacketCreator.showOwnBerserk(skilllevel, Berserk));
					getMap().broadcastMessage(MapleCharacter.this, MaplePacketCreator.showBerserk(getId(), skilllevel, Berserk), false);
				}
			}, 5000, 3000);
		}
	}

	public void cancelBeholder() {
        		LinkedList<MapleBuffStatValueHolder> allBuffs = new LinkedList<MapleBuffStatValueHolder>(effects.values());
        		for (MapleBuffStatValueHolder mbsvh : allBuffs) {
            		    if (mbsvh.effect.isBeholder()) {
                			cancelEffect(mbsvh.effect, false, mbsvh.startTime);
            			}
        		}
    	}
	private void prepareBeholderEffect() {
		if (beholderHealingSchedule != null && beholderBuffSchedule != null) {
			beholderHealingSchedule.cancel(false);
			beholderBuffSchedule.cancel(false);
		}
		if (getSkillLevel(SkillFactory.getSkill(1320008)) > 0) {
			final MapleStatEffect healEffect = SkillFactory.getSkill(1320008).getEffect(getSkillLevel(SkillFactory.getSkill(1320008)));
			beholderHealingSchedule = TimerManager.getInstance().register(new Runnable() {

				@Override
				public void run() {
					addHP(healEffect.getHp());
					getClient().getSession().write(MaplePacketCreator.showOwnBuffEffect(1321007, 2));
					getMap().broadcastMessage(MapleCharacter.this, MaplePacketCreator.summonSkill(getId(), 1321007, 5), true);
					getMap().broadcastMessage(MapleCharacter.this, MaplePacketCreator.showBuffeffect(getId(), 1321007, 2, (byte) 3), false);
				}
			}, healEffect.getX() * 1000, healEffect.getX() * 1000);
		}
		if (getSkillLevel(SkillFactory.getSkill(1320009)) > 0) {
			final MapleStatEffect buffEffect = SkillFactory.getSkill(1320009).getEffect(getSkillLevel(SkillFactory.getSkill(1320009)));
			beholderBuffSchedule = TimerManager.getInstance().register(new Runnable() {

				@Override
				public void run() {
					buffEffect.applyTo(MapleCharacter.this);
					getClient().getSession().write(MaplePacketCreator.showOwnBuffEffect(1321007, 2));
					getMap().broadcastMessage(MapleCharacter.this, MaplePacketCreator.summonSkill(getId(), 1321007, (int) (Math.random() * 3) + 6), true);
					getMap().broadcastMessage(MapleCharacter.this, MaplePacketCreator.showBuffeffect(getId(), 1321007, 2, (byte) 3), false);
				}
			}, buffEffect.getX() * 1000, buffEffect.getX() * 1000);
		}
	}

	public void setChalkboard(String text) {
		this.chalktext = text;
	}

	public String getChalkboard() {
		return this.chalktext;
	}

	public int getMarriageQuestLevel() {
		return marriageQuestLevel;
	}

	public void setMarriageQuestLevel(int nf) {
		marriageQuestLevel = nf;
	}

	public void addMarriageQuestLevel() {
		marriageQuestLevel += 1;
	}

	public void subtractMarriageQuestLevel() {
		marriageQuestLevel -= 1;
	}

	public void setCanTalk(int yesno) {
		this.canTalk = yesno;
	}

	public int getCanTalk() {
		return this.canTalk;
	}

	public void setZakumLevel(int level) {
		this.zakumLvl = level;
	}

	public int getZakumLevel() {
		return this.zakumLvl;
	}

	public void addZakumLevel() {
		this.zakumLvl += 1;
	}

	public void subtractZakumLevel() {
		this.zakumLvl -= 1;
	}

	public void setMarried(int mmm) {
		this.married = mmm;
	}

	public void setPartnerId(int pem) {
		this.partnerid = pem;
	}

	public int isMarried() {
		return married;
	}

	public MapleCharacter getPartner() {
		MapleCharacter test = this.getClient().getChannelServer().getPlayerStorage().getCharacterById(partnerid);
		if (test != null) {
			return test;
		}
		return null;
	}

	public int countItem(int itemid) {
		MapleInventoryType type = MapleItemInformationProvider.getInstance().getInventoryType(itemid);
		MapleInventory iv = inventory[type.ordinal()];
		int possesed = iv.countById(itemid);
		return possesed;
	}

    public void changePage(int page) {
		this.currentPage = page;
	}

	public void changeTab(int tab) {
		this.currentTab = tab;
	}

	public void changeType(int type) {
		this.currentType = type;
	}

	public int getCurrentPage() {
		return currentPage;
	}

	public int getCurrentTab() {
		return currentTab;
	}

	public int getCurrentType() {
		return currentType;
	}
}