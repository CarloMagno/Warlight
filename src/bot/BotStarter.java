package bot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import main.Region;
import move.AttackTransferMove;
import move.PlaceArmiesMove;

public class BotStarter implements Bot {

	public final static double SUPERIORITY_RATE = 0.6;	
	public final static double ATTACK_RATE = 0.7;	
	public final static double PANIC_RATE = 0.8;	

	/**
	 * A method used at the start of the game to decide which player start with what Regions. 6 Regions are required to be returned.
	 * Choices are the regions belonging to Oceania, plus Peru and Argentina
	 * @return : a list of m (m=6) Regions starting with the most preferred Region and ending with the least preferred Region to start with 
	 */
	@Override
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
			res = (neighbors.get(i).ownedByPlayer(me));
			i++;
		}

		return res;		
	}

	private List<RegionAdvantage> computeTroopDifferences(List<Region> myRegions, String myName) {

		List<RegionAdvantage> troopDifferences = new ArrayList<RegionAdvantage>();
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
			troopDifferences.add(new RegionAdvantage(r, troopDifferential));
		}		

		Collections.sort(troopDifferences);
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

		List<RegionAdvantage> neighborAdvantages = computeTroopDifferences(myRegions, myName);
		
		System.err.print("La lista ordenada es:");
		for (RegionAdvantage ra : neighborAdvantages) {
			System.err.print(ra);	
		}
		System.err.println();			
		/*
		 *  Take negative advantages (first ones in the list) and reinforce them
		 *  (the greater the disadvantage is, the higher the help is!)
		 */

		/* Rate calculation and assign such troop percentages to the endangered regions */
		int negativeAdvantageOverall = 0;
		int ind = 0;

		while ((ind < neighborAdvantages.size()) && (neighborAdvantages.get(ind).getDifference() < 0)) {
			negativeAdvantageOverall += neighborAdvantages.get(ind).getDifference();
			ind++;
		}

		System.err.println("Indice: " + ind + "; Neg Adv: " + negativeAdvantageOverall);

		// Variables for fair troop assignments
		float troopRate;
		int destinedTroops;

		// If there are disadvantages... reinforce them
		if (negativeAdvantageOverall < 0) {

			for (int i = 0; i < ind; i++) {
				if (neighborAdvantages.get(i).getDifference() < 0) {
					troopRate = neighborAdvantages.get(i).getDifference() / (float) negativeAdvantageOverall; 
					destinedTroops = (int) (initialTroops * troopRate);
					if ((armiesLeft >= destinedTroops) && (destinedTroops > 0)) {
						placeArmiesMoves.add(new PlaceArmiesMove(myName, neighborAdvantages.get(i).getRegion(), destinedTroops));
						armiesLeft -= destinedTroops;
					}
				}
			}	

		} else {

			/* More troops in every region than our enemy in sight, let's share the new ones fairly */

			for (int i = 0; i < neighborAdvantages.size(); i++) {
				negativeAdvantageOverall += neighborAdvantages.get(i).getDifference();
			}

			for (int i = 0; i < neighborAdvantages.size(); i++) {
				troopRate = neighborAdvantages.get(i).getDifference() / ((float) negativeAdvantageOverall); 
				destinedTroops = (int) (initialTroops * troopRate);
				if ((armiesLeft >= destinedTroops) && (destinedTroops > 0)) {
					placeArmiesMoves.add(new PlaceArmiesMove(myName, neighborAdvantages.get(i).getRegion(), destinedTroops));
					armiesLeft -= destinedTroops;
				}
			}	
		}

		// Assign remaining troops (remainder of percentages) to our less strong region
		if (armiesLeft > 0) {
			placeArmiesMoves.add(new PlaceArmiesMove(myName, neighborAdvantages.get(0).getRegion(), armiesLeft));
			armiesLeft -= armiesLeft;
		}

		return placeArmiesMoves;
	}


	@Override
	/**
	 * @return The list of PlaceArmiesMoves for one round
	 */
	public ArrayList<AttackTransferMove> getAttackTransferMoves(BotState state, Long timeOut) {

		ArrayList<AttackTransferMove> attackTransferMoves = new ArrayList<AttackTransferMove>();
		String myName = state.getMyPlayerName();

		for(Region fromRegion : state.getVisibleMap().getRegions()) {

			if(fromRegion.ownedByPlayer(myName)) {

				for (Region toRegion : fromRegion.getNeighbors()) {	

					// Panic mode and attacking moves
					//					if (!toRegion.ownedByPlayer(myName) && 
					//							toRegion.getArmies() >= (fromRegion.getArmies() * PANIC_RATE)) {
					//
					//						boolean escapeDestinationFound = false;
					//						List<Region> escapeRegions = fromRegion.getNeighbors();
					//						int i = 0;
					//
					//						while ((!escapeDestinationFound) && (i < escapeRegions.size())) {
					//							/* There's a place to escape! */
					//							if (escapeRegions.get(i).ownedByPlayer(myName)) {
					//								attackTransferMoves.add(new AttackTransferMove(myName, fromRegion, escapeRegions.get(i), fromRegion.getArmies()-1));
					//								escapeDestinationFound = true;
					//							}
					//						}
					//
					//						break;

					/*} else */
					if (!toRegion.ownedByPlayer(myName) && 
							toRegion.getArmies() <= ((int) fromRegion.getArmies() * SUPERIORITY_RATE)) {
						attackTransferMoves.add(new AttackTransferMove(myName, fromRegion, toRegion, (int) (fromRegion.getArmies() * ATTACK_RATE)));
					} 
				}				
			}

			// TODO Conquer SuperRegion

			/* Transfers from safe regions to unsafe regions */
			if(fromRegion.ownedByPlayer(myName) && isSafe(fromRegion, myName)) {

				List<Region> unsafeNeighbors = new ArrayList<Region>();
				int transferrableTroops = fromRegion.getArmies() - 1;
				int troopChunk;

				System.err.print(fromRegion.getId() + "-UNsafeNeighbors: ");

				/* Only transferring troops to our unsafe neighbors */
				for (Region potentialDestination : fromRegion.getNeighbors()) {
					if (potentialDestination.ownedByPlayer(myName) && ! isSafe(potentialDestination, myName)) {
						unsafeNeighbors.add(potentialDestination);
						System.err.print(potentialDestination.getId() + ",");
					}
				}
				System.err.println();


				// TODO We might improve this, distributing more troops to more weak endangered neighbors
				/* Dividing equally between unsafe neighbors */
				if (unsafeNeighbors.size() > 0) {	
					troopChunk = transferrableTroops / unsafeNeighbors.size();
					if (troopChunk > 0) {
						for (Region toRegion : unsafeNeighbors) {
							attackTransferMoves.add(new AttackTransferMove(myName, fromRegion, toRegion, troopChunk));
						}
					}
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
