import flanagan.analysis.*;
import java.util.Random;
import umontreal.iro.lecuyer.probdist.WeibullDist;

public class Weibull {
	
	public double k = 0;
	public double lambda = 0;
	public double delta = 0;
	public WeibullDist wd = new WeibullDist(1.0);
	
	public void setParameters(double k, double lambda, double delta){
		this.k = k;
		this.lambda = lambda;
		this.delta = delta;
	}
	
	public double getValue(double x){
		return 1-Math.exp(-Math.pow(lambda*(x-delta), k));
	}
	
	public void estimateParams(double[] data){
		int n = data.length;
		wd = wd.getInstanceFromMLE(data, n);
		k = wd.getAlpha();
		lambda = wd.getLambda();
		delta = wd.getDelta();
	}
	
	public double getCDF(double x){
		return wd.cdf(x);
	}
	
	public void init(){
	}
	
	public static void main(String[] args) {
		double[] data1 = new double[1000], data2 = new double[1000];
		Random gen = new Random();
		for (int i = 0; i < 1000; i++) {
			//data1[i] = gen.nextGaussian();
			data1[i] = gen.nextDouble();
		}
		Weibull b = new Weibull();
		b.estimateParams(data1);
		System.out.println(b.k +" " + b.lambda + "  " + b.delta);
		//Stat stat = new Stat(data1);
		//ProbabilityPlot probplot = new ProbabilityPlot(stat);
		//Regression reg = new Regression(data1, 1.0);
		//reg.weibullPlot();
		/*
		 * double mu = probplot.weibullMu(); double sigma =
		 * probplot.weibullSigma(); double gamma = probplot.weibullGamma();
		 * stat.weibullCDF(mu, sigma, gamma, 1.0);
		 */
		//probplot.weibullStandardProbabilityPlot();
		// stat.weibullCDF(1.0, 1.0, 1.0, 10.0);
	}
}
