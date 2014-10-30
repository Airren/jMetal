package org.uma.jmetal.algorithm.impl.singleobjective.geneticalgorithm;

import org.uma.jmetal.algorithm.impl.AbstractGeneticAlgorithm;
import org.uma.jmetal.operator.impl.crossover.SinglePointCrossover;
import org.uma.jmetal.operator.impl.mutation.BitFlipMutation;
import org.uma.jmetal.operator.impl.selection.BinaryTournamentSelection;
import org.uma.jmetal.problem.BinaryProblem;
import org.uma.jmetal.problem.singleobjective.OneMax;
import org.uma.jmetal.solution.BinarySolution;
import org.uma.jmetal.solution.Solution;
import org.uma.jmetal.util.comparator.ObjectiveComparator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Created by ajnebro on 26/10/14.
 */
public class SteadyStateGeneticAlgorithm extends AbstractGeneticAlgorithm<List<Solution>> {
  private Comparator<Solution> comparator = new ObjectiveComparator(0) ;
  private int maxIterations ;
  private int populationSize ;

  private BinaryProblem problem = new OneMax(512) ;

  public SteadyStateGeneticAlgorithm() {
    maxIterations = 25000 ;
    populationSize = 100 ;
    crossoverOperator = new SinglePointCrossover.Builder()
            .setProbability(0.9)
            .build() ;

    mutationOperator = new BitFlipMutation.Builder()
            .setProbability(1.0 / problem.getNumberOfBits(0))
            .build();

    selectionOperator = new BinaryTournamentSelection.Builder()
            .build();

  }

  @Override
  protected boolean isStoppingConditionReached() {
    return (getIterations() >= maxIterations) ;
  }

  @Override
  protected List<Solution> createInitialPopulation() {
    List<Solution> population = new ArrayList<>(populationSize) ;
    for (int i = 0; i < populationSize; i++) {
      Solution newIndividual = problem.createSolution();
      population.add(newIndividual);
    }
    return population;
  }

  @Override
  protected List<Solution> replacement(List<Solution> population, List<Solution> offspringPopulation) {
    population.sort(comparator);
    int worstSolutionIndex = population.size() - 1 ;
    if (comparator.compare(population.get(worstSolutionIndex), offspringPopulation.get(0))>0) {
      population.remove(worstSolutionIndex) ;
      population.add(offspringPopulation.get(0)) ;
    }

    return population;
  }

  @Override
  protected List<Solution> reproduction(List<Solution> matingPopulation) {
    List<Solution> offspringPopulation = new ArrayList<>(1) ;

    List<Solution<?>> parents = new ArrayList<>(2);
    parents.add(matingPopulation.get(0)) ;
    parents.add(matingPopulation.get(1));

    List<Solution> offspring = (List<Solution>) crossoverOperator.execute(parents);
    mutationOperator.execute(offspring.get(0)) ;

    offspringPopulation.add(offspring.get(0)) ;
    return offspringPopulation ;
  }

  @Override
  protected List<Solution> selection(List<Solution> population) {
    List<Solution> matingPopulation = new ArrayList<>(2) ;
    for (int i = 0; i < 2; i++) {
      Solution<?> solution = (Solution<?>) selectionOperator.execute(population);
      matingPopulation.add(solution) ;
    }

    return matingPopulation;
  }

  @Override
  protected List<Solution> evaluatePopulation(List<Solution> population) {
    for (Solution solution : population) {
      problem.evaluate((BinarySolution)solution);
    }

    return population ;
  }

  @Override
  public List<Solution> getResult() {
    getPopulation().sort(comparator);
    List<Solution> result = new ArrayList<>(1) ;
    result.add(getPopulation().get(0));
    return result;
  }
}
