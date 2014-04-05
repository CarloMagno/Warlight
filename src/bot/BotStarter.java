package bot;

import java.util.ArrayList;
import java.util.List;

import main.Region;
import move.AttackTransferMove;
import move.PlaceArmiesMove;

public class BotStarter implements Bot
{
    @Override
    /**
     * A method used at the start of the game to decide which player start with what Regions. 6 Regions are required to be returned.
     * Choices are the regions belonging to Oceania, plus Peru and Argentina
     * @return : a list of m (m=6) Regions starting with the most preferred Region and ending with the least preferred Region to start with
     */
    public ArrayList<Region> getPreferredStartingRegions(BotState state, Long timeOut)
    {
        int m = 6;
        ArrayList<Region> preferredStartingRegions = new ArrayList<Region>(m);

        List<Region> pickableRegions = state.getPickableStartingRegions();
		
		/* Giving priority to South America and Oceania */
        int currentSuperRegion;
        for (int i=0; i < pickableRegions.size(); i++) {
            currentSuperRegion = pickableRegions.get(i).getSuperRegion().getId();
            if ((currentSuperRegion == 2) || (currentSuperRegion == 6)) {
                preferredStartingRegions.add(pickableRegions.get(i));
            }
        }

		/* We finish with two random territories */
        double rand;
        int r;
        Region randomRegion;

        for (int i=0; i < 2; i++) {
            rand = Math.random();
            r = (int) (rand * pickableRegions.size());
            randomRegion = pickableRegions.get(i);

            while (preferredStartingRegions.contains(randomRegion)) {
                rand = Math.random();
                r = (int) (rand * pickableRegions.size());

            }

            preferredStartingRegions.add(randomRegion);
        }

        return preferredStartingRegions;
    }

    /**
     * A region is safe when no neighbor is under enemy control
     * @return True if a region is away from enemy immediate influence
     */
    private boolean isSafe(Region r, String me) {

        List<Region> neighbors = r.getNeighbors();

        int i = 0;
        boolean res = true;

        while ((i < neighbors.size()) && res) {
            if (! neighbors.get(i).ownedByPlayer(me)) {
                res = false;
            } else {
                i++;
            }
        }

        return res;
    }

    private List<Integer> computeTroopDifferences(List<Region> myRegions, String myName) {

        List<Integer> troopDifferences = new ArrayList<Integer>();
        int moreNeighborTroops, troopDifferential;
        Region currentNeighbor;

        for (Region r : myRegions) {
            List<Region> neighbors = r.getNeighbors();
            moreNeighborTroops = 0;

            for (int i = 0; i < neighbors.size(); i++) {
                currentNeighbor = neighbors.get(i);
                if (! currentNeighbor.ownedByPlayer(myName)) {
                    if (moreNeighborTroops < currentNeighbor.getArmies()) {
                        moreNeighborTroops = currentNeighbor.getArmies();
                    }
                }
            }

            troopDifferential = r.getArmies() - moreNeighborTroops;
            troopDifferences.add(troopDifferential);
        }

        return troopDifferences;

    }

    @Override
    /**
     * This method is called for at first part of each round. This bot gives priority to the borderline
     * outnumbered territories. Strongest neighbor criterion.
     * @return The list of PlaceArmiesMoves for one round
     */
    public ArrayList<PlaceArmiesMove> getPlaceArmiesMoves(BotState state, Long timeOut)
    {

        ArrayList<PlaceArmiesMove> placeArmiesMoves = new ArrayList<PlaceArmiesMove>();
        String myName = state.getMyPlayerName();
        int initialTroops = state.getStartingArmies();
        int armiesLeft = initialTroops;
        List<Region> visibleRegions = state.getVisibleMap().getRegions();
        List<Region> myRegions = new ArrayList<Region>();

        for (Region r : visibleRegions) {
            if (r.ownedByPlayer(myName)) {
                myRegions.add(r);
            }
        }

        List<Integer> neighborTroopAdvantage = computeTroopDifferences(myRegions, myName);

		/*
		 *  Take negative advantages (first ones in the sortedMap) and reinforce them
		 *  (the greater the disadvantage is, the higher the help is!)
		 */

		/* Rate calculation and assign such troop percentages to the endangered regions */
        int negativeAdvantageOverall = 0;
        int worstDifference = neighborTroopAdvantage.get(0);
        int worstDifferenceRegionIndex = 0;

        for (int i = 0; i < neighborTroopAdvantage.size(); i++) {
            if (neighborTroopAdvantage.get(i) < 0) {
                negativeAdvantageOverall -= neighborTroopAdvantage.get(i);
                if (worstDifference > neighborTroopAdvantage.get(i)) {
                    worstDifference = neighborTroopAdvantage.get(i);
                    worstDifferenceRegionIndex = i;
                }
            }
        }

        // Variables for fair troop assignments
        float troopRate;
        int destinedTroops;

        // If there are disadvantages... reinforce them
        if (negativeAdvantageOverall < 0) {

            for (int i = 0; i < neighborTroopAdvantage.size(); i++) {
                if (neighborTroopAdvantage.get(i) < 0) {
                    troopRate = neighborTroopAdvantage.get(i) / (float) negativeAdvantageOverall;
                    destinedTroops = (int) (initialTroops * troopRate);
                    if (destinedTroops > 0) {
                        placeArmiesMoves.add(new PlaceArmiesMove(myName, myRegions.get(i), destinedTroops));
                        armiesLeft -= destinedTroops;
                    }
                }
            }

        } else {

			/* More troops in every region than our enemy in sight, let's share the new ones fairly */

            for (int i = 0; i < neighborTroopAdvantage.size(); i++) {
                negativeAdvantageOverall += neighborTroopAdvantage.get(i);
            }

			/* Be careful with the new troop rate! (opposite meaning with positive troopAdvantages) */
            for (int i = 0; i < neighborTroopAdvantage.size(); i++) {
                troopRate = 1 - (neighborTroopAdvantage.get(i) / (float) negativeAdvantageOverall);
                destinedTroops = (int) (initialTroops * troopRate);
                if (destinedTroops > 0) {
                    placeArmiesMoves.add(new PlaceArmiesMove(myName, myRegions.get(i), destinedTroops));
                    armiesLeft -= destinedTroops;
                }
            }
        }

        // Assign remaining troops (remainder of percentages) to our less strong region
        if (armiesLeft > 0) {
            placeArmiesMoves.add(new PlaceArmiesMove(myName, myRegions.get(worstDifferenceRegionIndex), armiesLeft));
            armiesLeft -= armiesLeft;
        }

        return placeArmiesMoves;
    }


    @Override
    /**
     * This method is called for at the second part of each round. This example attacks if a region has
     * more than 6 armies on it, and transfers if it has less than 6 and a neighboring owned region.
     * @return The list of PlaceArmiesMoves for one round
     */
    public ArrayList<AttackTransferMove> getAttackTransferMoves(BotState state, Long timeOut)
    {
        ArrayList<AttackTransferMove> attackTransferMoves = new ArrayList<AttackTransferMove>();
        String myName = state.getMyPlayerName();
        int armies = 5;

        for(Region fromRegion : state.getVisibleMap().getRegions())
        {
            if(fromRegion.ownedByPlayer(myName)) //do an attack
            {
                ArrayList<Region> possibleToRegions = new ArrayList<Region>();
                possibleToRegions.addAll(fromRegion.getNeighbors());

                while(!possibleToRegions.isEmpty())
                {
                    double rand = Math.random();
                    int r = (int) (rand*possibleToRegions.size());
                    Region toRegion = possibleToRegions.get(r);

                    if(!toRegion.getPlayerName().equals(myName) && fromRegion.getArmies() > 6) //do an attack
                    {
                        attackTransferMoves.add(new AttackTransferMove(myName, fromRegion, toRegion, armies));
                        break;
                    }
                    else if(toRegion.getPlayerName().equals(myName) && fromRegion.getArmies() > 1) //do a transfer
                    {
                        attackTransferMoves.add(new AttackTransferMove(myName, fromRegion, toRegion, armies));
                        break;
                    }
                    else
                        possibleToRegions.remove(toRegion);
                }
            }
        }

        return attackTransferMoves;
    }

    public static void main(String[] args)
    {
        BotParser parser = new BotParser(new BotStarter());
        parser.run();
    }

}
