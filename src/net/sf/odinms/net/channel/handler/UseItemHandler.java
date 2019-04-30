package net.sf.odinms.net.channel.handler;

import net.sf.odinms.client.IItem;
import net.sf.odinms.client.MapleClient;
import net.sf.odinms.client.MapleInventoryType;
import net.sf.odinms.net.AbstractMaplePacketHandler;
import net.sf.odinms.server.MapleInventoryManipulator;
import net.sf.odinms.server.MapleItemInformationProvider;
import net.sf.odinms.server.maps.MapleMap;
import net.sf.odinms.tools.MaplePacketCreator;
import net.sf.odinms.tools.data.input.SeekableLittleEndianAccessor;

/**
 *
 * @author Matze
 */

public class UseItemHandler extends AbstractMaplePacketHandler {

    public void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {
        if (!c.getPlayer().isAlive()) {
            c.getSession().write(MaplePacketCreator.enableActions());
            return;
        }
        MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
        slea.readInt(); // i have no idea :) (o.o)
        byte slot = (byte)slea.readShort();
        int itemId = slea.readInt(); //as if we cared... ;)
        IItem toUse = c.getPlayer().getInventory(MapleInventoryType.USE).getItem(slot);
        if (toUse != null && toUse.getQuantity() > 0) {
            if (toUse.getItemId() != itemId) {
                return;
            }
           switch (toUse.getItemId()) {
                case 2030019:
                   MapleMap target = c.getChannelServer().getMapFactory().getMap(120000000);
                   c.getPlayer().changeMap(target, c.getChannelServer().getMapFactory().getMap(120000000).getPortal(0));
                   MapleInventoryManipulator.removeFromSlot(c, MapleInventoryType.USE, slot, (short) 1, false);
                   c.getSession().write(MaplePacketCreator.enableActions());
                   return;
                case 2030001:
                   MapleMap target1 = c.getChannelServer().getMapFactory().getMap(104000000);
                   c.getPlayer().changeMap(target1, c.getChannelServer().getMapFactory().getMap(104000000).getPortal(0));
                   MapleInventoryManipulator.removeFromSlot(c, MapleInventoryType.USE, slot, (short) 1, false);
                   c.getSession().write(MaplePacketCreator.enableActions());
                   return;
                case 2030002:
                   MapleMap target2 = c.getChannelServer().getMapFactory().getMap(101000000);
                   c.getPlayer().changeMap(target2, c.getChannelServer().getMapFactory().getMap(101000000).getPortal(0));
                   MapleInventoryManipulator.removeFromSlot(c, MapleInventoryType.USE, slot, (short) 1, false);
                   c.getSession().write(MaplePacketCreator.enableActions());
                   return;
                case 2030003:
                   MapleMap target3 = c.getChannelServer().getMapFactory().getMap(102000000);
                   c.getPlayer().changeMap(target3, c.getChannelServer().getMapFactory().getMap(102000000).getPortal(0));
                   MapleInventoryManipulator.removeFromSlot(c, MapleInventoryType.USE, slot, (short) 1, false);
                   c.getSession().write(MaplePacketCreator.enableActions());
                   return;
                case 2030004:
                   MapleMap target4 = c.getChannelServer().getMapFactory().getMap(100000000);
                   c.getPlayer().changeMap(target4, c.getChannelServer().getMapFactory().getMap(100000000).getPortal(0));
                   MapleInventoryManipulator.removeFromSlot(c, MapleInventoryType.USE, slot, (short) 1, false);
                   c.getSession().write(MaplePacketCreator.enableActions());
                   return;
                case 2030005:
                   MapleMap target5 = c.getChannelServer().getMapFactory().getMap(103000000);
                   c.getPlayer().changeMap(target5, c.getChannelServer().getMapFactory().getMap(103000000).getPortal(0));
                   MapleInventoryManipulator.removeFromSlot(c, MapleInventoryType.USE, slot, (short) 1, false);
                   c.getSession().write(MaplePacketCreator.enableActions());
                   return;
                case 2030006:
                   MapleMap target6 = c.getChannelServer().getMapFactory().getMap(105040300);
                   c.getPlayer().changeMap(target6, c.getChannelServer().getMapFactory().getMap(105040300).getPortal(0));
                   MapleInventoryManipulator.removeFromSlot(c, MapleInventoryType.USE, slot, (short) 1, false);
                   c.getSession().write(MaplePacketCreator.enableActions());
                   return;
                case 2030007:
                   MapleMap target7 = c.getChannelServer().getMapFactory().getMap(211041500);
                   c.getPlayer().changeMap(target7, c.getChannelServer().getMapFactory().getMap(211041500).getPortal(0));
                   MapleInventoryManipulator.removeFromSlot(c, MapleInventoryType.USE, slot, (short) 1, false);
                   c.getSession().write(MaplePacketCreator.enableActions());
                   return;
                case 2022178:
                case 2050004:
                   c.getPlayer().dispelDebuffs();
                   MapleInventoryManipulator.removeFromSlot(c, MapleInventoryType.USE, slot, (short) 1, false);
                   c.getSession().write(MaplePacketCreator.enableActions());
                   return;               
           }
            if (ii.isTownScroll(itemId)) {
                if (ii.getItemEffect(toUse.getItemId()).applyTo(c.getPlayer())) {
                    MapleInventoryManipulator.removeFromSlot(c, MapleInventoryType.USE, slot, (short) 1, false);
                }
                c.getSession().write(MaplePacketCreator.enableActions());
                return;
            }
            MapleInventoryManipulator.removeFromSlot(c, MapleInventoryType.USE, slot, (short) 1, false);
            ii.getItemEffect(toUse.getItemId()).applyTo(c.getPlayer());
            c.getPlayer().checkBerserk();
        } else {
            return;
        }
    }
}  