package features;

import java.util.ArrayList;

import common.Point;
import common.Signature;

public class FeatureExtractor {

	public FeatureExtractor() {
	}

	private GlobalFeatureVector computeGlobalFeatures(Signature s)
	{
		GlobalFeatureVector globVect = new GlobalFeatureVector();
		double totalLength = 0;
		double vxmoy = 0;
		double vymoy = 0;
		double vmoy = 0;
		double axmoy = 0;
		double aymoy = 0;
		double amoy = 0;
		double vmax = 0;
		double amax = 0;
		double angleSum = 0;
		double dxSum = 0;
		double dySum = 0;

		Point startPoint = s.getPoints().get(0);
		Point endPoint = s.getPoints().get(s.getPoints().size() - 1);
		double startEndDist = Math.sqrt(Math.pow(startPoint.getX() - endPoint.getX(), 2) + Math.pow(startPoint.getY() - endPoint.getY(), 2));
		double totalTime = endPoint.getTime() - startPoint.getTime();

		int i = 0;
		for (Point p : s.getPoints())
		{
			if (i < s.getPoints().size() - 1)
			{
				double dx = s.getPoints().get(i + 1).getX() - p.getX();
				double dy = s.getPoints().get(i + 1).getY() - p.getY();
				double eucliDist = Math.sqrt(dx * dx + dy * dy);

				double dt = s.getPoints().get(i + 1).getTime() - p.getTime();
				double dvx = dx / dt;
				double dvy = dy / dt;
				double dv = eucliDist / dt;
				double dax = dvx / dt;
				double day = dvy / dt;
				double da = dv / dt;

				dxSum += dx;
				dySum += dy;

				vxmoy += dvx;
				vymoy += dvy;
				vmoy += dv;
				axmoy += dax;
				aymoy += day;
				amoy += da;

				if (dv > vmax) {
					vmax = dv;
				}
				if (da > amax) {
					amax = da;
				}

				angleSum += Math.atan(dy / dx);

				totalLength += eucliDist;
			}

			i++;
		}

		vxmoy /= s.getPoints().size() - 1;
		vymoy /= s.getPoints().size() - 1;
		vmoy /= s.getPoints().size() - 1;
		axmoy /= s.getPoints().size() - 1;
		aymoy /= s.getPoints().size() - 1;
		amoy /= s.getPoints().size() - 1;

		globVect.add(totalLength);
		globVect.add(totalTime);
		globVect.add(startEndDist);
		globVect.add(vxmoy);
		globVect.add(vymoy);
		globVect.add(vmoy);
		globVect.add(vmax);
		globVect.add(axmoy);
		globVect.add(aymoy);
		globVect.add(amoy);
		globVect.add(amax);
		globVect.add(angleSum);
		globVect.add(dxSum / dySum);

		return globVect;
	}

	private LocalFeatureVector computeLocalFeatures(Signature s)
	{
		ArrayList<Double> pressure = new ArrayList<Double>();
		ArrayList<Double> times = new ArrayList<Double>();
		ArrayList<Double> posx = new ArrayList<Double>();
		ArrayList<Double> posy = new ArrayList<Double>();
		ArrayList<Double> posdx = new ArrayList<Double>();
		ArrayList<Double> posdy = new ArrayList<Double>();
		ArrayList<Double> posabsdx = new ArrayList<Double>();
		ArrayList<Double> posabsdy = new ArrayList<Double>();
		ArrayList<Double> alphacos = new ArrayList<Double>();
		ArrayList<Double> alphasin = new ArrayList<Double>();
		ArrayList<Double> curvature = new ArrayList<Double>();

		ArrayList<Double> vx = new ArrayList<Double>();
		ArrayList<Double> vy = new ArrayList<Double>();
		ArrayList<Double> ax = new ArrayList<Double>();
		ArrayList<Double> ay = new ArrayList<Double>();
		ArrayList<Double> criticalvx = new ArrayList<Double>();
		ArrayList<Double> criticalvy = new ArrayList<Double>();
		ArrayList<Double> criticalax = new ArrayList<Double>();
		ArrayList<Double> criticalay = new ArrayList<Double>();


		int i = 0;
		Point previousCriticalPoint = null;
		for (Point p : s.getPoints())
		{
			// Keep some basic features
			times.add((new Long(p.getTime())).doubleValue());
			posx.add(p.getX());
			posy.add(p.getY());
			pressure.add((new Integer(p.getPressure())).doubleValue());

			// Compute speed between critical points
			if (p.isCritical()) {
				if (p != null) {
					double dt = p.getTime() - previousCriticalPoint.getTime();
					double cvx = (p.getX() - previousCriticalPoint.getX()) / dt;
					double cvy = (p.getY() - previousCriticalPoint.getY()) / dt;
					double cax = cvx / dt;
					double cay = cvy / dt;

					criticalvx.add(cvx);
					criticalvy.add(cvy);
					criticalax.add(cax);
					criticalay.add(cay);
				}

				previousCriticalPoint = p;
			}

			// Compute spatial and dynamique features
			if (i < s.getPoints().size() - 1)
			{
				double dx = s.getPoints().get(i + 1).getX() - p.getX();
				double dy = s.getPoints().get(i + 1).getY() - p.getY();
				double dt = s.getPoints().get(i + 1).getTime() - p.getTime();
				double dvx = dx / dt;
				double dvy = dy / dt;
				double dax = dvx / dt;
				double day = dvy / dt;

				posdx.add(dx);
				posdy.add(dy);
				posabsdx.add(Math.abs(dx));
				posabsdy.add(Math.abs(dy));
				vx.add(dvx);
				vy.add(dvy);
				ax.add(dax);
				ay.add(day);

				double cosa = Math.abs(dx) / Math.sqrt(dx * dx + dy * dy);
				double sina = Math.abs(dy) / Math.sqrt(dx * dx + dy * dy);
				alphacos.add(cosa);
				alphasin.add(sina);
			}

			// Compute curvature
			if (i > 1 && i < s.getPoints().size() - 2)
			{
				// Al Kashi : a*a = b*b + c*c - 2bc*cos(BAC)
				double b = Math.sqrt(Math.pow(p.getX() - s.getPoints().get(i - 2).getX(), 2) + Math.pow(p.getY() - s.getPoints().get(i - 2).getY(), 2));
				double c = Math.sqrt(Math.pow(s.getPoints().get(i - 2).getX() - s.getPoints().get(i + 2).getX(), 2) + Math.pow(s.getPoints().get(i - 2).getY() - s.getPoints().get(i + 2).getY(), 2));
				double a = Math.sqrt(Math.pow(p.getX() - s.getPoints().get(i - 2).getX(), 2) + Math.pow(p.getY() - s.getPoints().get(i - 2).getY(), 2));
				double cosa = (b*b + c*c - a*a) / (2*b*c);
				curvature.add(cosa);
			}

			i++;
		}

		LocalFeatureVector locVect = new LocalFeatureVector();
		locVect.add(times);
		locVect.add(posx);
		locVect.add(posy);
		locVect.add(posdx);
		locVect.add(posdy);
		locVect.add(posabsdx);
		locVect.add(posabsdy);
		locVect.add(alphacos);
		locVect.add(alphasin);
		locVect.add(curvature);
		locVect.add(vx);
		locVect.add(vy);
		locVect.add(ax);
		locVect.add(ay);
		locVect.add(criticalvx);
		locVect.add(criticalvy);
		locVect.add(criticalax);
		locVect.add(criticalay);
		locVect.add(pressure);

		return locVect;
	}

	public GlobalFeatureVector extractGlobalFeature(Signature s)
	{
		GlobalFeatureVector features = computeGlobalFeatures(s);
		GlobalFeatureVector v = new GlobalFeatureVector();

		v.add(features.get(GlobalFeature.TOTAL_LENGTH.index));
		v.add(features.get(GlobalFeature.DURATION.index));
		v.add(features.get(GlobalFeature.START_END_DISTANCE.index));

		return v;
	}

	public LocalFeatureVector extractLocalFeature(Signature s)
	{
		LocalFeatureVector features = computeLocalFeatures(s);
		LocalFeatureVector v = new LocalFeatureVector();

		v.add(features.get(LocalFeature.POS_X.index));
		v.add(features.get(LocalFeature.POS_Y.index));

		return v;
	}

}