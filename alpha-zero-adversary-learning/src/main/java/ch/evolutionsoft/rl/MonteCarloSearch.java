package ch.evolutionsoft.rl;

import java.util.HashMap;
import java.util.Map;

import org.deeplearning4j.nn.graph.ComputationGraph;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

public class MonteCarloSearch {

  double cUct = 1.0;
  
  int numberOfSimulations = 50;
  
  int simulationsToEndDone = 0;
  
  Game game;

  ComputationGraph computationGraph;
  
  TreeNode rootNode;
  TreeNode treeNode;
  
  Map<INDArray, INDArray[]> neuralNetOutputsByBoardInputs = new HashMap<INDArray, INDArray[]>();
  
  public MonteCarloSearch(Game game, ComputationGraph computationGraph, AdversaryLearningConfiguration configuration) {
    
    this(game, computationGraph, configuration, game.getInitialBoard());
  }
  
  public MonteCarloSearch(Game game, ComputationGraph computationGraph, AdversaryLearningConfiguration configuration, INDArray currentBoard) {
    
    this.game = game;
    this.computationGraph = computationGraph;
    this.rootNode = new TreeNode(-1, game.getOtherPlayer(game.currentPlayer), 0, 1.0, 0.5, null);
    this.cUct = configuration.getCpUct();
    this.numberOfSimulations = configuration.getNummberOfMonteCarloSimulations();
  }
  
  void playout(INDArray board) {
    
    INDArray currentBoard = board.dup();
    
    while (!treeNode.children.isEmpty()) {
      
      treeNode = treeNode.selectMove(this.cUct);
      currentBoard = game.makeMove(currentBoard, treeNode.move, treeNode.lastColorMove);
    }
 
    INDArray[] neuralNetOutput;
    if (this.neuralNetOutputsByBoardInputs.containsKey(currentBoard)) {

      neuralNetOutput = this.neuralNetOutputsByBoardInputs.get(currentBoard);
      
    } else {
      
      long[] newShape = new long[currentBoard.shape().length + 1];
      System.arraycopy(currentBoard.shape(), 0, newShape, 1, currentBoard.shape().length);
      newShape[0] = 1;
      INDArray oneBatchBoard = currentBoard.reshape(newShape);
      neuralNetOutput = this.computationGraph.output(oneBatchBoard);
      this.neuralNetOutputsByBoardInputs.put(currentBoard, neuralNetOutput);
    }
    
    INDArray actionProbabilities = neuralNetOutput[0];
    double leafValue = neuralNetOutput[1].getDouble(0);
    
    if (game.gameEnded(currentBoard)) {

      this.simulationsToEndDone++;
      
      leafValue = 0.5f;
      
      if (game.hasWon(currentBoard, treeNode.lastColorMove)) {
        
        leafValue = 1f;
      
      // Not possible in TicTacToe, connect four, chess and so an, but in Go
      } else if (game.hasWon(currentBoard, game.getOtherPlayer(treeNode.lastColorMove))) {
        
        leafValue = 0f;
      }
    
    } else {
      
      treeNode.expand(game, actionProbabilities, currentBoard);
    }

    treeNode.updateRecursiv(leafValue);
  }

  public INDArray getActionValues(INDArray board, double temperature) {

    this.simulationsToEndDone = 0;
    
    while (this.simulationsToEndDone < numberOfSimulations) {

      this.treeNode = rootNode;
      this.playout(board.dup());
    }

    
    int[] visitedCounts = new int[game.getFieldCount()];
    int maxVisitedCounts = 0;

    for (int index = 0; index < game.getFieldCount(); index++) {
      
      if (this.rootNode.children.containsKey(index)) {
        
        visitedCounts[index] = this.rootNode.children.get(index).timesVisited;
        if (visitedCounts[index] > maxVisitedCounts) {
          
          maxVisitedCounts = visitedCounts[index];
        }
      
      } else {
        
        visitedCounts[index] = 0;
      }
    }
    
    INDArray moveProbabilities = Nd4j.zeros(game.getFieldCount());

    if (0 == temperature) {
      
      INDArray visitedCountsArray = Nd4j.createFromArray(visitedCounts);
      
      INDArray visitedCountsMax = visitedCountsArray.argMax(0);// Lower index is taken on equal max values
      
      moveProbabilities.putScalar(visitedCountsMax.getInt(0), 1);
      
      return moveProbabilities;
    }
    
    INDArray softmaxParameters = Nd4j.zeros(game.getFieldCount());
    for (int index = 0; index < game.getFieldCount(); index++) {

      softmaxParameters.putScalar(index, (1 / temperature) * Math.log(visitedCounts[index]) + 1e-8);
    }

    double maxSoftmaxParameter = softmaxParameters.getDouble(softmaxParameters.argMax(0).getInt(0));
    
    for (int index = 0; index < game.getFieldCount(); index++) {

      moveProbabilities.putScalar(index, Math.exp(softmaxParameters.getDouble(index) - maxSoftmaxParameter)); 
    }
    
    moveProbabilities = moveProbabilities.div(moveProbabilities.sumNumber());
    
    return moveProbabilities;
  }

  public void resetStoredOutputs() {
    
    this.neuralNetOutputsByBoardInputs.clear();
  }
  
  public double getcUct() {
    return cUct;
  }

  public void setcUct(double cUct) {
    this.cUct = cUct;
  }

  public int getNumberOfSimulations() {
    return numberOfSimulations;
  }

  public void setNumberOfSimulations(int numberOfSimulations) {
    this.numberOfSimulations = numberOfSimulations;
  }

  TreeNode updateWithMove(int lastMove) {
    
    if (this.rootNode.children.containsKey(lastMove)) {
      
      this.rootNode = this.rootNode.children.get(lastMove);
      return this.rootNode;
    }
    else {
      
      throw new IllegalArgumentException("no lastMove here.");
    }
  }
}
