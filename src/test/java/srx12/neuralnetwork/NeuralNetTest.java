package srx12.neuralnetwork;

import org.junit.jupiter.api.Test;
import srx12.matrix.Matrix;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NeuralNetTest {
    private Random random = new Random();

    @Test
    void testTrainEngine() {
        int inputRows = 500;
        int cols = 32;
        int outputRows = 3;

        Engine engine = new Engine();
        engine.add(Transform.DENSE,100,inputRows);
        engine.add(Transform.RELU);
        engine.add(Transform.DENSE,outputRows);
        engine.add(Transform.SOFTMAX);

        RunningAverages runningAverages = new RunningAverages(2,50,(callNumber, averages) -> {
            assertTrue(averages[0] < 6);
            //System.out.printf("%d. Loss: %.3f -- Percent Correct: %.2f\n",callNumber,averages[0],averages[1]);
        });

        double initialLearningRate = 0.2;
        double learningRate = initialLearningRate;
        double iterations = 500;

        for(int i=0;i<iterations;++i)
        {
            var tm = Util.generateTrainingMatrices(inputRows,outputRows,cols);
            var input = tm.getInput();
            var expected = tm.getOutput();

            //Engine Operation
            BatchResult batchResult = engine.runForwards(input);
            engine.runBackwards(batchResult, expected);
            engine.adjust(batchResult, 0.01);
            engine.evaluate(batchResult, expected);

            runningAverages.add(batchResult.getLoss(),batchResult.getPercentCorrect());

            learningRate -= (initialLearningRate/iterations);
        }
    }

    @Test
    void testWeightGradient()
    {
        int inputRows = 4;
        int outputRows = 5;

        Matrix weights = new Matrix(outputRows,inputRows, i->random.nextGaussian());
        Matrix input = Util.generateInputMatrix(inputRows,1);
        Matrix expected = Util.generateExpectedMatrix(outputRows,1);

        Matrix output = weights.multiplyMatrix(input).softmax();
        Matrix loss = LossFunctions.crossEntropy(expected,output);

        Matrix calculatedError = output.apply((index,value) -> value - expected.get(index));

        Matrix calculatedWeightGradients = calculatedError.multiplyMatrix(input.transpose());
        Matrix approximatedWeightGradients = Approximator.weightGradient(weights,w -> {
                    Matrix out = w.multiplyMatrix(input).softmax();
                    return LossFunctions.crossEntropy(expected,out);
                });

        calculatedWeightGradients.setTolerance(0.01);
        assertTrue(calculatedWeightGradients.equals(approximatedWeightGradients));
    }

    @Test
    public void test()
    {
        Matrix input = new Matrix(3,3,i->(i+1));
        Matrix weights = new Matrix(3,3,i->(i+1));
        Matrix biases = new Matrix(3,1,i->(i+1));

        Matrix result = weights.multiplyMatrix(input).modify((row,col,value)->value+biases.get(row));

        double[] expectedValues = {
                31.00000, 37.00000, 43.00000,
                68.00000, 83.00000, 98.00000,
                105.00000, 129.00000, 153.00000
        };

        Matrix expected = new Matrix(3,3,i->expectedValues[i]);

        assertEquals(result,expected);
    }

    @Test
    void testEngine(){

        int inputRows = 5;
        int cols = 6;
        int outputRows = 4;

        Engine engine = new Engine();

        engine.add(Transform.DENSE,8,5);
        engine.add(Transform.RELU);
        engine.add(Transform.DENSE,5);
        engine.add(Transform.RELU);
        engine.add(Transform.DENSE,4);

        engine.add(Transform.SOFTMAX);
        engine.setStoreInputError(true);

        Matrix input = Util.generateInputMatrix(inputRows,cols);
        Matrix expected = Util.generateExpectedMatrix(outputRows,cols);

        Matrix approximatedError = Approximator.gradient(input,in->{
            BatchResult batchResult = engine.runForwards(in);
            return LossFunctions.crossEntropy(expected,batchResult.getOutput());
        });
        BatchResult batchResult = engine.runForwards(input);
        engine.runBackwards(batchResult,expected);

        Matrix calculatedError = batchResult.getInputError();
        calculatedError.setTolerance(0.001);

        assertTrue(calculatedError.equals(approximatedError));
    }

    @Test
    void testBackpropWeights(){
        interface NeuralNet {
            Matrix apply(Matrix m);
        }

        final int inputRows = 4;
        final int outputRows = 4;
        final int cols = 5;

        Matrix input = (new Matrix(inputRows,cols, i->random.nextGaussian()) );
        Matrix expected = new Matrix(outputRows, cols, i->0);

        Matrix weights = new Matrix(outputRows, inputRows, i->random.nextGaussian());
        Matrix biases = new Matrix(outputRows,1,i->random.nextGaussian());

        for(int col=0;col<cols;++col){
            int randowRow = random.nextInt(outputRows);
            expected.set(randowRow,col,1);
        }
        NeuralNet neuralNet = m -> {
            Matrix out = m.apply((index, value) -> value > 0 ? value : 0);
            out = weights.multiplyMatrix(out); //Weights
            out.modify((row,col,value)->value+biases.get(row)); //Biases
            out = out.softmax();

            return out;
        };
        Matrix softmaxOutput = neuralNet.apply(input);

        Matrix approximatedResult = Approximator.gradient(input,in->{
            Matrix out = neuralNet.apply(in);
            return LossFunctions.crossEntropy(expected,out);
        });

        Matrix calculatedResult = softmaxOutput.apply((index,value)->value - expected.get(index));
        calculatedResult = weights.transpose().multiplyMatrix(calculatedResult); //Error of Weights
        calculatedResult = calculatedResult.apply((index,value) -> input.get(index)>0 ? value : 0); //Apply ReLU differential

        assertEquals(approximatedResult,calculatedResult);
    }


    @Test
    void testSoftmaxCrossEntropyGradient(){
        final int rows = 4;
        final int cols = 5;

        Matrix input = (new Matrix(rows,cols, i->random.nextGaussian()) );
        Matrix expected = new Matrix(rows, cols, i->0);

        for(int col=0;col<cols;++col){
            int randowRow = random.nextInt(rows);
            expected.set(randowRow,col,1);
        }

        Matrix softmaxOutput = input.softmax();

        Matrix result = Approximator.gradient(input,in->{
            return LossFunctions.crossEntropy(expected,in.softmax());
        });

        result.forEach((index,value)->{
            double softmaxValue = softmaxOutput.get(index);
            double expectedValue = expected.get(index);

            assertTrue(Math.abs(value - (softmaxValue - expectedValue))<0.01);
        });


    }

    //@Test
    void testApproximator(){
        final int rows = 4;
        final int cols = 5;

        Matrix input = (new Matrix(rows,cols, i->random.nextGaussian()) ).softmax();
        Matrix expected = new Matrix(rows, cols, i->0);

        for(int col=0;col<cols;++col){
            int randowRow = random.nextInt(rows);
            expected.set(randowRow,col,1);
        }

        Matrix result = Approximator.gradient(input,in->{
            return LossFunctions.crossEntropy(expected,in);
        });

        input.forEach((index,value)->{
            double resultValue = result.get(index);
            double expectedValue = expected.get(index);

            if(expectedValue < 0.0001){
                assertTrue(Math.abs(resultValue) < 0.01);
            }
            else{
                assertTrue(Math.abs(resultValue - -1.0/value) < 0.01);
            }
        });

        System.out.println(result);
    }

    //@Test
    void testCrossEntropy() {
        double[] expectedValues = {1, 0, 0, 0, 0, 1, 0, 1, 0};
        Matrix expected = new Matrix(3, 3, i -> expectedValues[i]);

        System.out.println(expected);

        Matrix actual = (new Matrix(3, 3, i -> 0.05 * (i * i))).softmax();
        System.out.println(actual);

        Matrix result = LossFunctions.crossEntropy(expected, actual);
        System.out.println(result);
        actual.forEach((row, col, index, value) -> {
            double expectedValue = expected.get(index);
            double loss = result.get(col);
            if (expectedValue > 0.9) {
                assertTrue(Math.abs(Math.log(value) + loss) < 0.0001);
            }
        });
    }

    //@Test
    void testTemp() {
        Engine engine = new Engine();
        int inputSize = 5;
        int layer1Size = 6;
        int layer2Size = 4;

        Matrix input = new Matrix(inputSize,1, i->random.nextGaussian());

        Matrix layer1Weights = new Matrix(layer1Size,input.getRows(),i->random.nextGaussian());
        Matrix layer1biases = new Matrix(layer1Size,1, i->random.nextGaussian());

        Matrix layer2Weights = new Matrix(layer2Size, layer1Weights.getRows(), i->random.nextGaussian());
        Matrix layer2biases = new Matrix(layer2Size, 1, i->random.nextGaussian());

        var output = input;
        System.out.println(output);

        //Multiply with Layer Weights
        output = layer1Weights.multiplyMatrix(output);
        //Add Biases to the Matrix
        output = output.modify((row,col,value) -> value + layer1biases.get(row));
        //Perform ReLU
        output = output.modify(value -> value > 0 ? value : 0);


        //Multiply with Layer Weights
        output = layer2Weights.multiplyMatrix(output);
        //Add Biases to the Matrix
        output = output.modify((row,col,value) -> value + layer2biases.get(row));
        //Perform ReLU
        output = output.softmax();

        System.out.println(output);
    }

    @Test
    public void testSoftmax(){
        Matrix m = new Matrix(5,8, i-> random.nextGaussian());
        Matrix result = m.softmax();

        double[] colSums = new double[8];

        result.forEach((row,col,value)->{
            assertTrue(value>=0 && value <= 1.0);
            colSums[col] += value;
        });

        for(var colSum: colSums){
            assertTrue(Math.abs(colSum - 1.0) < 0.0001);
        }
    }

    @Test
    void testReLu() {
        final int numberNeurons = 5;
        final int numberInputs = 6;
        final int inputSize = 4;

        Matrix input = new Matrix(inputSize,numberInputs, i->random.nextDouble());
        Matrix weights = new Matrix(numberNeurons,inputSize,i->random.nextGaussian());
        Matrix biases = new Matrix(numberNeurons,1,i->random.nextGaussian());

        Matrix result1 = weights.multiplyMatrix(input).modify((row,col,value) -> value + biases.get(row));
        Matrix result2 = weights.multiplyMatrix(input).modify((row,col,value) -> value + biases.get(row)).modify(value -> value>0? value : 0);

        result2.forEach((index,value)->{
            double originalValue = result1.get(index);
            if(originalValue > 0){
                assertTrue(Math.abs(originalValue-value) < 0.0001);
            }
            else
                assertTrue(Math.abs(value) < 0.0001);
        });
    }

}
