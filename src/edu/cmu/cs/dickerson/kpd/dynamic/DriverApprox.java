package edu.cmu.cs.dickerson.kpd.dynamic;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import edu.cmu.cs.dickerson.kpd.fairness.io.DriverApproxOutput;
import edu.cmu.cs.dickerson.kpd.fairness.io.DriverApproxOutput.Col;
import edu.cmu.cs.dickerson.kpd.helper.IOUtil;
import edu.cmu.cs.dickerson.kpd.solver.CycleFormulationCPLEXSolver;
import edu.cmu.cs.dickerson.kpd.solver.GreedyPackingSolver;
import edu.cmu.cs.dickerson.kpd.solver.exception.SolverException;
import edu.cmu.cs.dickerson.kpd.solver.solution.Solution;
import edu.cmu.cs.dickerson.kpd.structure.Cycle;
import edu.cmu.cs.dickerson.kpd.structure.Pool;
import edu.cmu.cs.dickerson.kpd.structure.alg.CycleGenerator;
import edu.cmu.cs.dickerson.kpd.structure.alg.CycleMembership;
import edu.cmu.cs.dickerson.kpd.structure.generator.PoolGenerator;
import edu.cmu.cs.dickerson.kpd.structure.generator.SaidmanPoolGenerator;
import edu.cmu.cs.dickerson.kpd.structure.generator.UNOSGenerator;

public class DriverApprox {

	public static void main(String[] args) {

		// Set up our random generators for pools (sample from UNOS data, sample from Saidman distribution)
		Random r = new Random();
		UNOSGenerator UNOSGen = UNOSGenerator.makeAndInitialize(IOUtil.getBaseUNOSFilePath(), ',', r);
		SaidmanPoolGenerator SaidmanGen = new SaidmanPoolGenerator(r);

		// List of generators we want to use
		List<PoolGenerator> genList = Arrays.asList(new PoolGenerator[] {UNOSGen, SaidmanGen});

		// list of |V|s we'll iterate over
		List<Integer> graphSizeList = Arrays.asList(new Integer[] {300});
		int numGraphReps = 16; 

		// Cycle and chain limits
		int cycleCap = 3;
		int chainCap = 0;

		// Number of greedy packings per solve call
		int numGreedyReps = 100;

		// Store output
		String path = "approx_" + System.currentTimeMillis() + ".csv";
		DriverApproxOutput out = null;
		try {
			out = new DriverApproxOutput(path);
		} catch(IOException e) {
			e.printStackTrace();
			return;
		}

		for(int graphSize : graphSizeList) {
			for(PoolGenerator gen : genList) {
				for(int graphRep=0; graphRep<numGraphReps; graphRep++) {

					IOUtil.dPrintln("Graph (|V|=" + graphSize + ", #" + graphRep + "/" + numGraphReps + "), gen: " + gen.getClass().getSimpleName());

					// Generate pool (no alts for now)
					Pool pool = gen.generate(graphSize, 0);
					
					// Bookkeeping
					out.set(Col.CHAIN_CAP, chainCap);	
					out.set(Col.CYCLE_CAP, cycleCap);	
					out.set(Col.NUM_PAIRS, pool.getNumPairs());
					out.set(Col.NUM_ALTS, pool.getNumAltruists());
					out.set(Col.GENERATOR, gen.getClass().getSimpleName());
					out.set(Col.APPROX_REP_COUNT, numGreedyReps);

					// Generate all cycles in pool
					CycleGenerator cg = new CycleGenerator(pool);
					List<Cycle> cycles = cg.generateCyclesAndChains(cycleCap, chainCap, false);
					CycleMembership membership = new CycleMembership(pool, cycles);

					// Get optimal match size for pool
					Solution optSol = null;
					try {
						CycleFormulationCPLEXSolver s = new CycleFormulationCPLEXSolver(pool, cycles, membership);
						optSol = s.solve();
						IOUtil.dPrintln("Optimal Value: " + optSol.getObjectiveValue());
					} catch(SolverException e) {
						e.printStackTrace();
						System.exit(-1);
					}

					// Get a greedy packing
					Solution greedySol = null;
					try {
						GreedyPackingSolver s = new GreedyPackingSolver(pool, cycles, membership);
						greedySol = s.solve(numGreedyReps);
						IOUtil.dPrintln("Greedy Value: " + greedySol.getObjectiveValue());
					} catch(SolverException e) {
						e.printStackTrace();
						System.exit(-1);
					}

					// Compare the greedy and optimal solutions
					if(null != optSol) {
						out.set(Col.OPT_OBJECTIVE, optSol.getObjectiveValue());	
						out.set(Col.OPT_RUNTIME, optSol.getSolveTime());
					}

					if(null != greedySol) {
						out.set(Col.APPROX_OBJECTIVE, greedySol.getObjectiveValue());		
						out.set(Col.APPROX_RUNTIME, greedySol.getSolveTime());
					}

					// Write the  row of data
					try {
						out.record();
					} catch(IOException e) {
						IOUtil.dPrintln("Had trouble writing experimental output to file.  We assume this kills everything; quitting.");
						e.printStackTrace();
						System.exit(-1);
					}

				} // graphRep : numGraphReps
			} // gen : genList
		} // graphSize : graphSizeList

		// clean up CSV writer
		if(null != out) {
			try {
				out.close();
			} catch(IOException e) {
				e.printStackTrace();
			}
		}

		IOUtil.dPrintln("======= DONE =======");
	}

}
