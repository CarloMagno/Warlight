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
	public final static double COMBO_MIN_RATE = 0.6;
	public final static double COMBO_ATTACK_RATE = 0.85;
	public final static double SUCCESS_RATE = 0.7;

	public final static int WORLD_DOMINANCE_LIMIT = 30;
	public final static int COMBO_MIN_TROOPS = 10;

	private static int ownedRegions;
	private static String myName = "";
	private static String opponentName = "";

	private List<Region> visibleRegions;
	private List<Region> fortifiableRegions;

	/**
	 * A method used at the start of the game to decide which player start with what Regions. 6 Regions are required to be returned.
	 * First choices are the regions belonging to Oceania and South America
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

		/* We finish with two "not so" random territories */
		Region randomRegion;

		for (int i=0; i < 2; i++) {
			randomRegion = pickableRegions.get(i);
			preferredStartingRegions.add(randomRegion);
		}

		return preferredStartingRegions;
	}

	/**
	 * A region is safe when no neighbor is under enemy/neutral control
	 * @return True if a region is away from enemy/neutral immediate influence
	 */
	private boolean isSafe(Region r, String playerName) {

		List<Region> neighbors = r.getNeighbors();

		int i = 0;
		boolean res = true;

		while ((i < neighbors.size()) && res) {
			res = (neighbors.get(i).ownedByPlayer(playerName));
			i++;
		}

		return res;		
	}
	
	/**
	 * A region is threatened when at least one neighbor is under enemy control
	 * @return True if a region is away from enemy immediate influence
	 */
	private boolean isThreatened(Region r, String opponentName) {

		List<Region> neighbors = r.getNeighbors();

		int i = 0;
		boolean res = false;

		while ((i < neighbors.size()) && !res) {
			res = (neighbors.get(i).ownedByPlayer(opponentName));
			i++;
		}

		return res;		
	}

	private List<RegionAdvantage> computeTroopDifferences() {

		List<RegionAdvantage> troopDifferences = new ArrayList<RegionAdvantage>();
		int moreNeighborTroops, troopDifferential;
		Region currentNeighbor;

		for (Region r : fortifiableRegions) {
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


	private boolean comboAttackChance(String myName, Region origin, Region target) {

		List<Region> targetNeighbors = target.getNeighbors();
		Region comboPartnerRegion;

		int i = 0, comboPartnerTroops;
		int targetTroops = target.getArmies();
		int originTroops = origin.getArmies();
		boolean res = false;

		if (targetNeighbors.contains(origin) && (originTroops >= COMBO_MIN_TROOPS) &&
				(targetTroops <= originTroops)) {
			while ((i < targetNeighbors.size()) && !res) {
				comboPartnerRegion = targetNeighbors.get(i);
				comboPartnerTroops  = comboPartnerRegion.getArmies();
				res = (!origin.equals(comboPartnerRegion)) && (comboPartnerRegion.ownedByPlayer(myName)) && 
						(comboPartnerTroops >= COMBO_MIN_TROOPS) && 
						(targetTroops <= comboPartnerTroops);
				i++;
			}
		}

		return res;	
	}
	

	/**
	 * Returns the estimated attacking troops in order to conquer the enemy region
	 * with the indicated success rate .
	 * @param enemyRegion
	 * @return
	 */
	private int estimateAttackingTroops(Region enemyRegion) {

		int res = 0;
		int enemyArmies = enemyRegion.getArmies();

		res = (int)Math.ceil(enemyArmies/(1.0 - SUCCESS_RATE));

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
		
		if (myName.equals("")) {
			myName = state.getMyPlayerName();	
			opponentName = state.getOpponentPlayerName();
		}
		
		int initialTroops = state.getStartingArmies();
		int armiesLeft = initialTroops;
		
		visibleRegions = state.getVisibleMap().getRegions();
		fortifiableRegions = new ArrayList<Region>();

		for (Region r : visibleRegions) {
			if (r.ownedByPlayer(myName) && ! isSafe(r,myName)) {
				fortifiableRegions.add(r);
			}
		}

		List<RegionAdvantage> neighborAdvantages = computeTroopDifferences();

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

		int newOwnedRegionCount = 0;
		int presentTroops, neighborTroops, attackEstimation, comboEstimation, neutralAttackEstimation;
		 

		for(Region fromRegion : visibleRegions) {

			List<Region> neighbors = fromRegion.getNeighbors();
			List<Region> unsafeNeighbors = new ArrayList<Region>();

			// Neutral-neighbored region (1st case) or enemy-neighbored region (2nd case) 
			if(fromRegion.ownedByPlayer(myName)) {

				newOwnedRegionCount++;
				presentTroops = fromRegion.getArmies();
				
				for (Region toRegion : neighbors) {	
					neighborTroops = toRegion.getArmies();
					attackEstimation = estimateAttackingTroops(toRegion);

					if (toRegion.ownedByPlayer(myName) && ! isSafe(toRegion, myName)) {
						unsafeNeighbors.add(toRegion);
					}
					
//					if (toRegion.ownedByPlayer(opponentName) && comboAttackChance(myName, fromRegion, toRegion)) {
//						attackTransferMoves.add(new AttackTransferMove(myName, fromRegion, toRegion, (int) (presentTroops * COMBO_ATTACK_RATE)));
//						presentTroops -= (int) (presentTroops * COMBO_ATTACK_RATE);
//					} else if (!toRegion.ownedByPlayer(myName) && (! isThreatened(fromRegion,opponentName)) && 
//							(presentTroops > 2) && (neighborTroops < ((int) presentTroops * SUPERIORITY_RATE))) {
//						attackTransferMoves.add(new AttackTransferMove(myName, fromRegion, toRegion, (int) (presentTroops * ATTACK_NEUTRAL_RATE)));
//						presentTroops -= (int) (presentTroops * ATTACK_NEUTRAL_RATE);
//					} else if (toRegion.ownedByPlayer(opponentName) && (isThreatened(fromRegion, opponentName)) && 
//							(presentTroops > 2) && (neighborTroops < ((int) presentTroops * SUPERIORITY_RATE))) {
//						attackTransferMoves.add(new AttackTransferMove(myName, fromRegion, toRegion, (int) (presentTroops * ATTACK_RATE)));
//						presentTroops -= (int) (presentTroops * ATTACK_RATE);
//					} 
					
					comboEstimation = (int) (presentTroops * COMBO_ATTACK_RATE);
					neutralAttackEstimation = presentTroops - 1;
					
					if (toRegion.ownedByPlayer(opponentName) && comboAttackChance(myName, fromRegion, toRegion)) {
						attackTransferMoves.add(new AttackTransferMove(myName, fromRegion, toRegion, comboEstimation));
						presentTroops -= comboEstimation;
					} else if (toRegion.ownedByPlayer(opponentName) && (isThreatened(fromRegion, opponentName)) && 
							(presentTroops > attackEstimation)) {
						attackTransferMoves.add(new AttackTransferMove(myName, fromRegion, toRegion, attackEstimation));
						presentTroops -= attackEstimation;
					} else if (!toRegion.ownedByPlayer(myName) && (! isThreatened(fromRegion,opponentName)) && 
							(presentTroops > 2) && (neighborTroops < ((int) presentTroops * SUPERIORITY_RATE))) {
						attackTransferMoves.add(new AttackTransferMove(myName, fromRegion, toRegion, neutralAttackEstimation));
						presentTroops -= neutralAttackEstimation;
					}
					
				}				
			}

			/* Transfers from safe regions to unsafe regions */
			if((ownedRegions < WORLD_DOMINANCE_LIMIT) && fromRegion.ownedByPlayer(myName) && isSafe(fromRegion, myName)) {

				int transferrableTroops = fromRegion.getArmies() - 1;
				int troopChunk;

				if (transferrableTroops > 0) {

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

		ownedRegions = newOwnedRegionCount;
		return attackTransferMoves;
	}

	public static void main(String[] args)
	{
		BotParser parser = new BotParser(new BotStarter());
		parser.run();
	}

}
