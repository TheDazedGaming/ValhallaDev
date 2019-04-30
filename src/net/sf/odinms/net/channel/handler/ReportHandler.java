package net.sf.odinms.net.channel.handler;

import net.sf.odinms.client.MapleClient;
import net.sf.odinms.net.AbstractMaplePacketHandler;
import net.sf.odinms.tools.data.input.SeekableLittleEndianAccessor;
import java.sql.*;

import net.sf.odinms.database.DatabaseConnection;
import net.sf.odinms.net.world.remote.WorldChannelInterface;
import net.sf.odinms.tools.MaplePacketCreator;

public class ReportHandler extends AbstractMaplePacketHandler {
     	private static String getCharInfoById(int id){
        	try {
                Connection dcon = DatabaseConnection.getConnection();
                PreparedStatement ps = dcon.prepareStatement("SELECT * FROM characters where id = ?");
                ps.setInt(1, id);
                ResultSet rs = ps.executeQuery();
                if(rs.next())
                    return rs.getString("name");
            } catch(Exception ex) {}
                return "error while trying to get name";
    	}

    	final String[] reasons = {"Hacking", "Botting", "Scamming", "Fake GM", "Harassment", "Advertising"};

	@Override
	public void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c)  {
        	int reportedCharId = slea.readInt();
            byte reason = slea.readByte();
            String chatlog = "No chatlog";
            short clogLen = slea.readShort();
            if(clogLen > 0)
                chatlog = slea.readAsciiString(clogLen);
            boolean reported = addReportEntry(c.getPlayer().getId(), reportedCharId, reason, chatlog);
            int cid = reportedCharId;
            StringBuilder sb = new StringBuilder();
            sb.append(c.getPlayer().getName());
            sb.append(" reported character ");
            sb.append(getCharInfoById(cid));
            sb.append(" for ");
            sb.append(reasons[reason]);
            sb.append(".");

            if(reported)
                c.getSession().write(MaplePacketCreator.reportReply((byte)0));
            else
                c.getSession().write(MaplePacketCreator.reportReply((byte)4));
            WorldChannelInterface wci = c.getChannelServer().getWorldInterface();
            try{wci.broadcastGMMessage(null, MaplePacketCreator.serverNotice(5 , sb.toString()).getBytes());} catch(Exception ex){}
    }

    public boolean addReportEntry(int reporterId, int victimId, byte reason, String chatlog) {
        try {
            Connection dcon = DatabaseConnection.getConnection();
            PreparedStatement ps;
            ps = dcon.prepareStatement("INSERT INTO reports (`reporttime`, `reporterid`, `victimid`, `reason`, `chatlog`, `status`) VALUES (CURRENT_TIMESTAMP, ?, ?, ?, ?, 'UNHANDLED')");
            ps.setInt(1, reporterId);
            ps.setInt(2, victimId);
            ps.setInt(3, reason);
            ps.setString(4, chatlog);
            ps.executeUpdate();
            ps.close();
            } catch (Exception ex){
                return false;
            }
            return true;
    }
}