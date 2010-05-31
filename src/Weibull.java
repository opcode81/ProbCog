import flanagan.analysis.*;
import java.util.Random;

public class Weibull {

	public static void main(String[] args) {
		double[] data1 = new double[1000], data2 = new double[1000];
		Random gen = new Random();
		for (int i = 0; i < 1000; i++) {
			data1[i] = gen.nextGaussian();
			//data2[i] = gen.nextDouble();
		}
		//Stat stat = new Stat(xdata);
		//ProbabilityPlot probplot = new ProbabilityPlot(stat);
		Regression reg = new Regression(data1,1.0);
		reg.weibullPlot();
		/*double mu = probplot.weibullMu();
		double sigma = probplot.weibullSigma();
		double gamma = probplot.weibullGamma();
		stat.weibullCDF(mu, sigma, gamma, 1.0);*/
		//probplot.weibullStandardProbabilityPlot();
		//stat.weibullCDF(1.0, 1.0, 1.0, 10.0);
	}
}
