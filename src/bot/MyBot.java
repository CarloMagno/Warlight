package bot;

import main.Region;
import move.AttackTransferMove;
import move.PlaceArmiesMove;

import java.util.ArrayList;

/**
 * Created with IntelliJ IDEA.
 * User: Carlitos
 * Date: 2/04/14
 * Time: 16:18
 * To change this template use File | Settings | File Templates.
 */
public class MyBot implements Bot {


    @Override
    public ArrayList<Region> getPreferredStartingRegions(BotState state, Long timeOut) {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public ArrayList<PlaceArmiesMove> getPlaceArmiesMoves(BotState state, Long timeOut) {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public ArrayList<AttackTransferMove> getAttackTransferMoves(BotState state, Long timeOut) {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public static void main(String[] args)
    {
        BotParser parser = new BotParser(new BotStarter());
        parser.run();
    }
}
