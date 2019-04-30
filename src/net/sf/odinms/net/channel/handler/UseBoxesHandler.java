package net.sf.odinms.net.channel.handler;

import net.sf.odinms.net.AbstractMaplePacketHandler;
import net.sf.odinms.tools.MaplePacketCreator;
import net.sf.odinms.tools.data.input.SeekableLittleEndianAccessor;
import net.sf.odinms.server.*;
import net.sf.odinms.client.*;

/**
 *
 * @author EC15
 * TODO : capture the packets that being send when players open the box.
 */
public class UseBoxesHandler extends AbstractMaplePacketHandler {
private final static int[] goldboxPrizes = {1102041, 2340000, 2049100, 4001115, 1382036, 2022182, 2022121, 1452018, 2044302, 1472033, 4001116, 1302049, 2290052, 2290090, 2290072, 2290096, 4032015, 4032016, 4032017};
private final static int[] silverboxPrizes = {2022123, 1022060, 1402044, 1002391, 1102041, 1102042, 2044602, 2044402, 2044302, 2044702, 1092050, 1472051, 1452019, 2101013, 1422030, 3010009, 2022121, 1452018, 2388017, 1302049, 2022182, 4001116};
// all the items has been taken from http://www.basilmarket.com/forum/844573/1//List_of_Gachapon_Boxes_Items.html#

    public void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {
               slea.readByte();
               slea.readByte();
               int box = slea.readInt();
               if (c.getPlayer().haveItem(5490000, 1, false, true)) {
                   if (box == 4280000) { // gold
                         int itemId = goldboxPrizes[(int) (Math.random() * goldboxPrizes.length)];
                         MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
                         if (ii.getInventoryType(itemId) == MapleInventoryType.EQUIP) {
                             ii.randomizeStats((Equip) ii.getEquipById(itemId));
                         }
                           MapleInventoryManipulator.addById(c, itemId, (short) 1, null, -1);
                   } else if (box== 4280001) {//silver
                         int itemId = silverboxPrizes[(int) (Math.random() * silverboxPrizes.length)];
                         MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
                         if (ii.getInventoryType(itemId) == MapleInventoryType.EQUIP) {
                             ii.randomizeStats((Equip) ii.getEquipById(itemId));
                         }
                           MapleInventoryManipulator.addById(c, itemId, (short) 1, null, -1);
                   } else {
                       return;
                   }
                   MapleInventoryManipulator.removeById(c, MapleItemInformationProvider.getInstance().getInventoryType(box), box, 1, true, false);
                   MapleInventoryManipulator.removeById(c, MapleItemInformationProvider.getInstance().getInventoryType(5490000), 5490000, 1, true, false);
                } else {
                   c.getSession().write(MaplePacketCreator.serverNotice(5, "You need a Master Key to open the box!"));
               }
        }
}