package preprocessing.reducer;

import java.util.ArrayList;

import common.Point;
import common.Signature;

public class SpeedReducer {

	/**
	 * Reduce the number of points by computing speed and keeping only local minimums.
	 * @param signature The signature to reduce.
	 */
	public void apply(Signature signature) {
		int n = signature.getPoints().size();

		/* Compute speed for each points except first and last one */
		ArrayList<Point> points = signature.getPoints();
		ArrayList<Double> speeds = new ArrayList<Double>();
		speeds.add(0.); // Add first speed to maintain indexes

		for (int i = 1; i < n - 1; i++) {
			double distance = Point.distance(points.get(i - 1), points.get(i))
					+ Point.distance(points.get(i), points.get(i + 1));
			double time = points.get(i).getTime() - points.get(i - 1).getTime()
					+ points.get(i + 1).getTime() - points.get(i).getTime();

			speeds.add(distance / time);
		}

		// Add last
		speeds.add(Point.distance(points.get(n - 2), points.get(n - 1))
				/ points.get(n - 1).getTime() - points.get(n - 2).getTime());

		int nbCritical = 0;

		boolean incr = false;
		for (int i = 1; i < n - 1; i++) {
			//Decreasing
			if (speeds.get(i) >= speeds.get(i + 1)) {
				incr = false;
			}
			// First increase -> Local minimum
			else if (!incr){
				incr = true;
				signature.getPoints().get(i).setCritical(true);
				nbCritical++;
			}
			// Keep increasing
			else {
				// Do nothing
			}
		}

		System.out.println("Speed: " + nbCritical + " critical points found");
	}
}
