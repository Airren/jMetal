//  FastPGA_main.java
//
//  Author:
//       Antonio J. Nebro <antonio@lcc.uma.es>
//       Juan J. Durillo <durillo@lcc.uma.es>
//
//  Copyright (c) 2011 Antonio J. Nebro, Juan J. Durillo
//
//  This program is free software: you can redistribute it and/or modify
//  it under the terms of the GNU Lesser General Public License as published by
//  the Free Software Foundation, either version 3 of the License, or
//  (at your option) any later version.
//
//  This program is distributed in the hope that it will be useful,
//  but WITHOUT ANY WARRANTY; without even the implied warranty of
//  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//  GNU Lesser General Public License for more details.
// 
//  You should have received a copy of the GNU Lesser General Public License
//  along with this program.  If not, see <http://www.gnu.org/licenses/>.
package org.uma.jmetal.runner.multiobjective;

import java.io.IOException;
import java.util.HashMap;
import java.util.logging.FileHandler;
import java.util.logging.Logger;

import org.uma.jmetal.core.Algorithm;
import org.uma.jmetal.core.Operator;
import org.uma.jmetal.core.Problem;
import org.uma.jmetal.core.SolutionSet;
import org.uma.jmetal.metaheuristic.multiobjective.fastpga.FastPGA;
import org.uma.jmetal.operator.crossover.CrossoverFactory;
import org.uma.jmetal.operator.mutation.MutationFactory;
import org.uma.jmetal.operator.selection.BinaryTournament;
import org.uma.jmetal.problem.Kursawe;
import org.uma.jmetal.problem.ProblemFactory;
import org.uma.jmetal.qualityIndicator.QualityIndicatorGetter;
import org.uma.jmetal.util.Configuration;
import org.uma.jmetal.util.JMetalException;
import org.uma.jmetal.util.comparator.FPGAFitnessComparator;

/**
 * Class for configuring and running the FastPGA algorithm
 */
public class FastPGARunner {
  public static Logger logger_;      
  public static FileHandler fileHandler_; 

  /**
   * @param args Command line arguments. The first (optional) argument specifies
   *             the problem to solve.
   * @throws org.uma.jmetal.util.JMetalException
   */
  public static void main(String[] args) throws JMetalException, IOException, ClassNotFoundException {
    Problem problem;
    Algorithm algorithm;
    Operator crossover;
    Operator mutation;
    Operator selection;

    QualityIndicatorGetter indicators;

    // Logger object and file to store log messages
    logger_ = Configuration.logger;
    fileHandler_ = new FileHandler("FastPGA_main.log");
    logger_.addHandler(fileHandler_);

    indicators = null;
    if (args.length == 1) {
      Object[] params = {"Real"};
      problem = (new ProblemFactory()).getProblem(args[0], params);
    } else if (args.length == 2) {
      Object[] params = {"Real"};
      problem = (new ProblemFactory()).getProblem(args[0], params);
      indicators = new QualityIndicatorGetter(problem, args[1]);
    } else {
      problem = new Kursawe("Real", 3);
      //problem = new Kursawe("BinaryReal", 3);
      //problem = new Water("Real");
      //problem = new ZDT1("ArrayReal", 100);
      //problem = new ConstrEx("Real");
      //problem = new DTLZ1("Real");
      //problem = new OKA2("Real") ;
    }

    algorithm = new FastPGA();
    algorithm.setProblem(problem);

    algorithm.setInputParameter("maxPopSize", 100);
    algorithm.setInputParameter("initialPopulationSize", 100);
    algorithm.setInputParameter("maxEvaluations", 25000);
    algorithm.setInputParameter("a", 20.0);
    algorithm.setInputParameter("b", 1.0);
    algorithm.setInputParameter("c", 20.0);
    algorithm.setInputParameter("d", 0.0);

    // Parameter "termination"
    // If the preferred stopping criterium is PPR based, termination must 
    // be set to 0; otherwise, if the algorithm is intended to iterate until 
    // a give number of evaluations is carried out, termination must be set to 
    // that number
    algorithm.setInputParameter("termination", 1);

    // Mutation and Crossover for Real codification 
    HashMap<String, Object> crossoverParameters = new HashMap<String, Object>();
    crossoverParameters.put("probability", 0.9);
    crossoverParameters.put("distributionIndex", 20.0);
    crossover = CrossoverFactory.getCrossoverOperator("SBXCrossover", crossoverParameters);
    //crossover.setParameter("probability",0.9);                   
    //crossover.setParameter("distributionIndex",20.0);

    HashMap<String, Object> mutationParameters = new HashMap<String, Object>();
    mutationParameters.put("probability", 1.0 / problem.getNumberOfVariables());
    mutationParameters.put("distributionIndex", 20.0);
    mutation = MutationFactory.getMutationOperator("PolynomialMutation", mutationParameters);
    // Mutation and Crossover for Binary codification

    HashMap<String, Object> selectionParameters = new HashMap<String, Object>();
    selectionParameters.put("comparator", new FPGAFitnessComparator());
    selection = new BinaryTournament(selectionParameters);

    algorithm.addOperator("crossover", crossover);
    algorithm.addOperator("mutation", mutation);
    algorithm.addOperator("selection", selection);

    long initTime = System.currentTimeMillis();
    SolutionSet population = algorithm.execute();
    long estimatedTime = System.currentTimeMillis() - initTime;

    // Result messages 
    logger_.info("Total execution time: " + estimatedTime + "ms");
    logger_.info("Variables values have been writen to file VAR");
    population.printVariablesToFile("VAR");
    logger_.info("Objectives values have been writen to file FUN");
    population.printObjectivesToFile("FUN");

    if (indicators != null) {
      logger_.info("Quality indicators");
      logger_.info("Hypervolume: " + indicators.getHypervolume(population));
      logger_.info("GD         : " + indicators.getGD(population));
      logger_.info("IGD        : " + indicators.getIGD(population));
      logger_.info("Spread     : " + indicators.getSpread(population));
      logger_.info("Epsilon    : " + indicators.getEpsilon(population));

      int evaluations = ((Integer) algorithm.getOutputParameter("evaluations")).intValue();
      logger_.info("Speed      : " + evaluations + " evaluations");
    }
  }
}
