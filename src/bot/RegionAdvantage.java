package bot;

import main.Region;

public class RegionAdvantage implements Comparable<RegionAdvantage> {

	/* 
	 * This class wraps information about a Region and the strongest
	 * enemy neighbor for easing troop distribution in the first stage of the round.
	 */
	
	private Region region;
	private int maximumTroopDifference;
	
	public RegionAdvantage(Region r, int diff) {
		region = r;
		maximumTroopDifference = diff;
	}
	
	public Region getRegion() {
		return region;
	}
	
	public int getDifference() {
		return maximumTroopDifference;
	}
	
	@Override
	public int compareTo(RegionAdvantage otherRegion) {
		if (this.maximumTroopDifference < otherRegion.getDifference()) {
			return -1;
		} else if (this.maximumTroopDifference > otherRegion.getDifference()) {
			return 1;
		} else {
			return 0;
		}
	}
	

}
