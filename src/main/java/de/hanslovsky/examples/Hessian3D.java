package de.hanslovsky.examples;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import bdv.util.BdvFunctions;
import bdv.util.BdvHandle;
import bdv.util.BdvStackSource;
import ij.ImageJ;
import ij.ImagePlus;
import ij.plugin.FolderOpener;
import ij.process.ImageConverter;
import net.imglib2.FinalInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.gauss3.Gauss3;
import net.imglib2.algorithm.gradient.HessianMatrix;
import net.imglib2.algorithm.linalg.eigen.TensorEigenValues;
import net.imglib2.converter.read.ConvertedRandomAccessibleInterval;
import net.imglib2.exception.IncompatibleTypeException;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.DoubleArray;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.outofbounds.OutOfBoundsBorderFactory;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Intervals;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

public class Hessian3D
{
	public static void main( final String[] args ) throws IncompatibleTypeException, InterruptedException, ExecutionException
	{

//		final String url = "http://img.autobytel.com/car-reviews/autobytel/11694-good-looking-sports-cars/2016-Ford-Mustang-GT-burnout-red-tire-smoke.jpg";
		final String dir = "/data/hanslovskyp/davi_toy_set/substacks/scale=0.4/data";

		final ImagePlus imp = new FolderOpener().openFolder( dir );
		new ImageConverter( imp ).convertToGray32();

		final double min = imp.getDisplayRangeMin();
		final double max = imp.getDisplayRangeMax();

		final IntervalView< FloatType > wrapped = Views.interval( ImageJFunctions.wrapFloat( imp ), new FinalInterval( imp.getWidth(), imp.getHeight(), imp.getStackSize() / 3 ) );

		final BdvStackSource< FloatType > raw = BdvFunctions.show( wrapped, "raw" );
		final BdvHandle bdv = raw.getBdvHandle();
		raw.setDisplayRange( min, max );

		//
		final double sig = 2.0;
		final double[] sigma = new double[] { 1.0 * sig, 1.0 * sig, 0.1 * sig };

		final int nThreads = Runtime.getRuntime().availableProcessors();
		final ExecutorService es = Executors.newFixedThreadPool( nThreads );

		final long[] dim = Intervals.dimensionsAsLongArray( wrapped );
		final ArrayImg< DoubleType, DoubleArray > gaussian = ArrayImgs.doubles( dim );
		Gauss3.gauss( sigma, Views.extendBorder( wrapped ), gaussian );

		final RandomAccessibleInterval< DoubleType > hessian =
				HessianMatrix.calculateMatrix(
						Views.extendBorder( gaussian ),
						ArrayImgs.doubles( dim[ 0 ], dim[ 1 ], dim[ 2 ], 3 ),
						ArrayImgs.doubles( dim[ 0 ], dim[ 1 ], dim[ 2 ], 6 ),
						new OutOfBoundsBorderFactory<>(),
						nThreads,
						es );


		final RandomAccessibleInterval< DoubleType > evs = TensorEigenValues.calculateEigenValuesSymmetric( hessian, TensorEigenValues.createAppropriateResultImg( hessian, new ArrayImgFactory<>(), new DoubleType() ), nThreads, es );

		es.shutdown();

		new ImageJ();
		for ( int d = 0; d < evs.dimension( evs.numDimensions() - 1 ); ++d )
		{
			final IntervalView< DoubleType > hs = Views.hyperSlice( evs, evs.numDimensions() - 1, d );
			double minVal = Double.MAX_VALUE;
			double maxVal = -Double.MIN_VALUE;
			for ( final DoubleType h : hs )
			{
				final double dd = h.get();
				minVal = Math.min( dd, minVal );
				maxVal = Math.max( dd, maxVal );
			}

			final DoubleType finalMinVal = new DoubleType( minVal );
			final double norm = 1.0 / ( maxVal - minVal );
			final double maxIntensity = 255;
			final double factor = maxIntensity * norm;

			final ConvertedRandomAccessibleInterval< DoubleType, DoubleType > hsStretched = new ConvertedRandomAccessibleInterval<>( hs, ( s, t ) -> {
				t.set( s );
				t.sub( finalMinVal );
				t.mul( factor );
			}, new DoubleType() );
			ImageJFunctions.show( hs );

		}


	}
}
