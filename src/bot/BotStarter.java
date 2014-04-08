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
	public final static double ATTACK_NEUTRAL_RATE = 0.8;	
	public final static double COMBO_MIN_RATE = 0.8;
	public final static double COMBO_ATTACK_RATE = 0.8;
	public final static double PANIC_RATE = 0.3;	

	public final static int WORLD_DOMINANCE_LIMIT = 30;
	public final static int SUPERPOWER_TROOP_LIMIT = 100;
	public final static int COMBO_MIN_TROOPS = 12;

	private static int ownedRegions;

	/**
	 * A method used at the start of the game to decide which player start with what Regions. 6 Regions are required to be returned.
	 * Choices are the regions belonging to Oceania, plus Peru and Argentina
	 * @return : a list of m (m=6) Regions starting with the most preferred Region and ending with the least preferred Region to start with 
	 */
	@Override
	public ArrayList<Region> getPreferredStartingRegions(BotState state, Long timeOut) {
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
			randomRegion = pickableRegions.get(r);

			while (preferredStartingRegions.contains(randomRegion)) {
				rand = Math.random();
				r = (int) (rand * pickableRegions.size());

			}

			preferredStartingRegions.add(randomRegion);
		}

		return preferredStartingRegions;
	}

//	/**
//	 * A region is safe when no neighbor is under enemy/neutral control
//	 * @return True if a region is away from enemy/neutral immediate influence
//	 */
//	private boolean isSafe(Region r, String me) {
//
//		List<Region> neighbors = r.getNeighbors();
//
//		int i = 0;
//		boolean res = true;
//
//		while ((i < neighbors.size()) && res) {
//			res = (neighbors.get(i).ownedByPlayer(me));
//			i++;
//		}
//
//		return res;		
//	}

//	/**
//	 * A region is threatened when at least one neighbor is under enemy control
//	 * @return True if a region is away from enemy immediate influence
//	 */
//	private boolean isThreatened(Region r, String opponentName) {
//
//		List<Region> neighbors = r.getNeighbors();
//
//		int i = 0;
//		boolean res = false;
//
//		while ((i < neighbors.size()) && !res) {
//			res = (neighbors.get(i).ownedByPlayer(opponentName));
//			i++;
//		}
//
//		return res;		
//	}

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

	private void countOwnedRegions(List<Region> visibleRegions, String myName) {
		int ownedRegionCount = 0;

		for (Region r : visibleRegions) {
			if (r.ownedByPlayer(myName)) {
				ownedRegionCount++;
			}
		}

		ownedRegions = ownedRegionCount;
	}

	/* Combo attacks cover this case */
	//	private boolean havePowerfulNeighbor(Region r, String myName) {
	//		List<Region> neighbors = r.getNeighbors();
	//
	//		int i = 0;
	//		boolean res = false;
	//
	//		while ((i < neighbors.size()) && !res) {
	//			res = (neighbors.get(i).ownedByPlayer(myName) && (neighbors.get(i).getArmies() > SUPERPOWER_TROOP_LIMIT));
	//			i++;
	//		}
	//
	//		return res;	
	//	}

	private boolean comboAttackChance(String myName, Region origin, Region target) {

		List<Region> targetNeighbors = target.getNeighbors();
		Region comboPartnerRegion;

		int i = 0, comboPartnerTroops;
		int targetTroops = target.getArmies();
		int originTroops = origin.getArmies();
		boolean res = false;

		if (targetNeighbors.contains(origin) && (originTroops > COMBO_MIN_TROOPS) &&
				(targetTroops <= ((int) originTroops * COMBO_MIN_RATE))) {
			while ((i < targetNeighbors.size()) && !res) {
				comboPartnerRegion = targetNeighbors.get(i);
				comboPartnerTroops  = comboPartnerRegion.getArmies();
				res = (!origin.equals(comboPartnerRegion)) && (comboPartnerRegion.ownedByPlayer(myName)) && 
						(comboPartnerTroops > COMBO_MIN_TROOPS) && 
						(targetTroops <= ((int) comboPartnerTroops * COMBO_MIN_RATE));
				i++;
			}
		}

		return res;	
	}


	@Override
	/**
	 * This method is called for at first part of each round. This bot gives priority to the borderline 
	 * outnumbered territories. Strongest neighbor criterion.
	 * @return The list of PlaceArmiesMoves for one round
	 */
	public ArrayList<PlaceArmiesMove> getPlaceArmiesMoves(BotState state, Long timeOut) {

		ArrayList<PlaceArmiesMove> placeArmiesMoves = new ArrayList<PlaceArmiesMove>();
		String myName = state.getMyPlayerName();
		int initialTroops = state.getStartingArmies();
		int armiesLeft = initialTroops;
		List<Region> visibleRegions = state.getVisibleMap().getRegions();
		List<Region> fortifiableRegions = new ArrayList<Region>();

		for (Region r : visibleRegions) {
			if (r.ownedByPlayer(myName) && !r.isSafe()) {
				fortifiableRegions.add(r);
			}
		}

		List<RegionAdvantage> neighborAdvantages = computeTroopDifferences(fortifiableRegions, myName);

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
		String opponentName = state.getOpponentPlayerName();
		List<Region> visibleRegions = state.getVisibleMap().getRegions();

		countOwnedRegions(visibleRegions, myName);

		for(Region fromRegion : state.getVisibleMap().getRegions()) {

			// TODO Panic mode and attacking moves
			List<Region> neighbors = fromRegion.getNeighbors();

			//			int neighborInd = 0, i = 0;
			//			boolean escapeDestinationFound = false;
			//
			//			while (!escapeDestinationFound && (neighborInd < neighbors.size())) {
			//				Region inspectedNeighbor = neighbors.get(neighborInd);
			//				if (!inspectedNeighbor.ownedByPlayer(myName) &&	
			//						fromRegion.getArmies() <= ((int) Math.ceil((inspectedNeighbor.getArmies() * PANIC_RATE)))) {
			//
			//					List<Region> escapeRegions = fromRegion.getNeighbors();
			//					i = 0;
			//
			//					while ((!escapeDestinationFound) && (i < escapeRegions.size())) {
			//						/* There's a place to escape! */
			//						if (escapeRegions.get(i).ownedByPlayer(myName)) {
			//							attackTransferMoves.add(new AttackTransferMove(myName, fromRegion, escapeRegions.get(i), fromRegion.getArmies()-1));
			//							escapeDestinationFound = true;
			//						}
			//					}
			//				}
			//				neighborInd++;
			//			}

			// Neutral-neighbored region (1st case) or enemy-neighbored region (2nd case) 
			if(fromRegion.ownedByPlayer(myName)) {

				for (Region toRegion : neighbors) {	
					int neighborTroops = toRegion.getArmies();
					int presentTroops = fromRegion.getArmies();

					if (toRegion.ownedByPlayer(opponentName) && comboAttackChance(myName, fromRegion, toRegion)) {
						attackTransferMoves.add(new AttackTransferMove(myName, fromRegion, toRegion, (int) (fromRegion.getArmies() * COMBO_ATTACK_RATE)));
					} else if (!toRegion.ownedByPlayer(myName) && (!fromRegion.isThreatened(opponentName)) && 
							(presentTroops > 2) && (neighborTroops < ((int) presentTroops * SUPERIORITY_RATE))) {
						attackTransferMoves.add(new AttackTransferMove(myName, fromRegion, toRegion, (int) (fromRegion.getArmies() * ATTACK_NEUTRAL_RATE)));
					} else if (toRegion.ownedByPlayer(opponentName) && (fromRegion.isThreatened(opponentName)) && 
							(presentTroops > 2) && (neighborTroops < ((int) presentTroops * SUPERIORITY_RATE))) {
						attackTransferMoves.add(new AttackTransferMove(myName, fromRegion, toRegion, (int) (fromRegion.getArmies() * ATTACK_RATE)));
					} 
				}				
			}

			// Kamikaze attack to destroy enemy strong defenses (Combo attacks cover this case)
			//			if(fromRegion.ownedByPlayer(myName) && (fromRegion.getArmies() > SUPERPOWER_TROOP_LIMIT)) {
			//
			//				for (Region toRegion : neighbors) {	
			//					if (!toRegion.ownedByPlayer(myName) && 
			//							(toRegion.getArmies() > SUPERPOWER_TROOP_LIMIT) && havePowerfulNeighbor(fromRegion, myName)) {
			//						attackTransferMoves.add(new AttackTransferMove(myName, fromRegion, toRegion, fromRegion.getArmies()-1));
			//					} 
			//				}				
			//			}

			/* Transfers from safe regions to unsafe regions */
			if((ownedRegions < WORLD_DOMINANCE_LIMIT) && fromRegion.ownedByPlayer(myName) && fromRegion.isSafe()) {

				List<Region> unsafeNeighbors = new ArrayList<Region>();
				int transferrableTroops = fromRegion.getArmies() - 1;
				int troopChunk;

				if (transferrableTroops > 0) {
					/* Only transferring troops to our unsafe neighbors */
					for (Region potentialDestination : neighbors) {
						if (potentialDestination.ownedByPlayer(myName) && !potentialDestination.isSafe()) {
							unsafeNeighbors.add(potentialDestination);
						}
					}

					// TODO We might improve this, distributing more troops to more weak endangered neighbors
					/* Dividing equally between unsafe neighbors */
					if ((unsafeNeighbors.size() > 0)) {	
						troopChunk = (int) (transferrableTroops / unsafeNeighbors.size());
						if (troopChunk > 0) {
							for (Region toRegion : unsafeNeighbors) {
								attackTransferMoves.add(new AttackTransferMove(myName, fromRegion, toRegion, troopChunk));
							}
						}
					} else if (unsafeNeighbors.size() == 0) {
						troopChunk = (int) (transferrableTroops / neighbors.size());
						if (troopChunk > 0) {
							for (Region toRegion : neighbors) {
								attackTransferMoves.add(new AttackTransferMove(myName, fromRegion, toRegion, troopChunk));
							}
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
