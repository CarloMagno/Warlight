package bot;

import main.Region;

public class RegionAdvantage implements Comparable<RegionAdvantage> {

	/* 
	 * This class wraps information about a Region and the strongest
	 * enemy neighbor for easing troop distribution in the first stage of the round.
	 */
	
	private Region region;
	private int maxTroopDifference;
	
	public RegionAdvantage(Region r, int diff) {
		region = r;
		maxTroopDifference = diff;
	}
	
	public Region getRegion() {
		return region;
	}
	
	public int getDifference() {
		return maxTroopDifference;
	}
	
	@Override
	public int compareTo(RegionAdvantage otherRegion) {
		if (this.maxTroopDifference < otherRegion.getDifference()) {
			return -1;
		} else if (this.maxTroopDifference > otherRegion.getDifference()) {
			return 1;
		} else {
			return 0;
		}
	}
	
	@Override
	public String toString() {
		return region.getId() + ": " + maxTroopDifference + ";";
	}

}
