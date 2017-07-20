package spim.process.interestpointdetection.methods.downsampling;

import static mpicbg.spim.data.generic.sequence.ImgLoaderHints.LOAD_COMPLETELY;

import java.util.Date;
import java.util.List;

import bdv.img.hdf5.Hdf5ImageLoader;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.sequence.ImgLoader;
import mpicbg.spim.data.sequence.MultiResolutionImgLoader;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.VoxelDimensions;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Util;
import spim.fiji.spimdata.interestpoints.InterestPoint;

public class DownsampleTools
{
	protected static final int[] ds = { 1, 2, 4, 8 };

	public static String[] availableDownsamplings( final AbstractSpimData< ? > data, final ViewId viewId )
	{
		final String[] dsStrings;

		if (MultiResolutionImgLoader.class.isInstance( data.getSequenceDescription().getImgLoader() ))
		{
			final MultiResolutionImgLoader mrImgLoader = (MultiResolutionImgLoader) data.getSequenceDescription().getImgLoader();
			final double[][] mipmapResolutions = mrImgLoader.getSetupImgLoader( viewId.getViewSetupId()).getMipmapResolutions();
			dsStrings = new String[mipmapResolutions.length];
			
			for (int i = 0; i<mipmapResolutions.length; i++)
			{
				final String fx = ((Long)Math.round( mipmapResolutions[i][0] )).toString(); 
				final String fy = ((Long)Math.round( mipmapResolutions[i][1] )).toString(); 
				final String fz = ((Long)Math.round( mipmapResolutions[i][2] )).toString();
				final String dsString = String.join( ", ", fx, fy, fz );
				dsStrings[i] = dsString;
			}
		}
		else
		{
			dsStrings = new String[]{ "1, 1, 1" };
		}

		return dsStrings;
	}

	public static long[] parseDownsampleChoice( final String dsChoice )
	{
		final long[] downSamplingFactors = new long[ 3 ];
		final String[] choiceSplit = dsChoice.split( ", " );
		downSamplingFactors[0] = Long.parseLong( choiceSplit[0] );
		downSamplingFactors[1] = Long.parseLong( choiceSplit[1] );
		downSamplingFactors[2] = Long.parseLong( choiceSplit[2] );
		
		return downSamplingFactors;
	}
	public static void correctForDownsampling( final List< InterestPoint > ips, final AffineTransform3D t )
	{
		IOFunctions.println("(" + new Date(System.currentTimeMillis()) + "): Correcting coordinates for downsampling using AffineTransform: " + t );

		if ( ips == null || ips.size() == 0 )
		{
			IOFunctions.println("(" + new Date(System.currentTimeMillis()) + "): WARNING: List is empty." );
			return;
		}

		final double[] tmp = new double[ ips.get( 0 ).getL().length ];

		for ( final InterestPoint ip : ips )
		{
			t.apply( ip.getL(), tmp );

			ip.getL()[ 0 ] = tmp[ 0 ];
			ip.getL()[ 1 ] = tmp[ 1 ];
			ip.getL()[ 2 ] = tmp[ 2 ];

			t.apply( ip.getW(), tmp );

			ip.getW()[ 0 ] = tmp[ 0 ];
			ip.getW()[ 1 ] = tmp[ 1 ];
			ip.getW()[ 2 ] = tmp[ 2 ];
		}
	}

	public static int downsampleFactor( final int downsampleXY, final int downsampleZ, final VoxelDimensions v )
	{
		final double calXY = Math.min( v.dimension( 0 ), v.dimension( 1 ) );
		final double calZ = v.dimension( 2 ) * downsampleZ;
		final double log2ratio = Math.log( calZ / calXY ) / Math.log( 2 );

		final double exp2;

		if ( downsampleXY == 0 )
			exp2 = Math.pow( 2, Math.floor( log2ratio ) );
		else
			exp2 = Math.pow( 2, Math.ceil( log2ratio ) );

		return (int)Math.round( exp2 );
	}

	public static RandomAccessibleInterval< FloatType > openAtLowestLevelFloat(
			final ImgLoader imgLoader,
			final ViewId view )
	{
		return openAtLowestLevelFloat( imgLoader, view, null );
	}

	public static RandomAccessibleInterval< FloatType > openAtLowestLevelFloat(
			final ImgLoader imgLoader,
			final ViewId view,
			final AffineTransform3D t )
	{
		final RandomAccessibleInterval< FloatType > input;

		if ( MultiResolutionImgLoader.class.isInstance( imgLoader ) )
		{
			final MultiResolutionImgLoader mrImgLoader = ( MultiResolutionImgLoader ) imgLoader;
			final double[][] mipmapResolutions = mrImgLoader.getSetupImgLoader( view.getViewSetupId() ).getMipmapResolutions();
			final int bestLevel = findLowestResolutionLevel( mrImgLoader, view );

			IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Loading level " + Util.printCoordinates( mipmapResolutions[ bestLevel ] ) );

			input = mrImgLoader.getSetupImgLoader( view.getViewSetupId() ).getFloatImage( view.getTimePointId(), bestLevel, false );
			if ( t != null )
				t.set( mrImgLoader.getSetupImgLoader( view.getViewSetupId() ).getMipmapTransforms()[ bestLevel ] );
		}
		else
		{
			IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Loading full-resolution images :( " );

			input = imgLoader.getSetupImgLoader( view.getViewSetupId() ).getFloatImage( view.getTimePointId(), false );
			if ( t != null )
				t.identity();
		}

		return input;
	}

	public static RandomAccessibleInterval openAtLowestLevel(
			final ImgLoader imgLoader,
			final ViewId view )
	{
		return openAtLowestLevel( imgLoader, view, null );
	}

	public static RandomAccessibleInterval openAtLowestLevel(
			final ImgLoader imgLoader,
			final ViewId view,
			final AffineTransform3D t )
	{
		final RandomAccessibleInterval input;

		if ( MultiResolutionImgLoader.class.isInstance( imgLoader ) )
		{
			final MultiResolutionImgLoader mrImgLoader = ( MultiResolutionImgLoader ) imgLoader;
			final double[][] mipmapResolutions = mrImgLoader.getSetupImgLoader( view.getViewSetupId() ).getMipmapResolutions();
			final int bestLevel = findLowestResolutionLevel( mrImgLoader, view );

			IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Loading level " + Util.printCoordinates( mipmapResolutions[ bestLevel ] ) );

			input = mrImgLoader.getSetupImgLoader( view.getViewSetupId() ).getImage( view.getTimePointId(), bestLevel );
			if ( t != null )
				t.set( mrImgLoader.getSetupImgLoader( view.getViewSetupId() ).getMipmapTransforms()[ bestLevel ] );
		}
		else
		{
			IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Loading full-resolution images :( " );

			input = imgLoader.getSetupImgLoader( view.getViewSetupId() ).getImage( view.getTimePointId() );
			if ( t != null )
				t.identity();
		}

		return input;
	}

	public static int findLowestResolutionLevel( final MultiResolutionImgLoader mrImgLoader, final ViewId view )
	{
		final double[][] mipmapResolutions = mrImgLoader.getSetupImgLoader( view.getViewSetupId() ).getMipmapResolutions();

		int maxMul = Integer.MIN_VALUE;
		int bestLevel = -1;

		for ( int i = 0; i < mipmapResolutions.length; ++i )
		{
			int mul = 1;

			for ( int d = 0; d < mipmapResolutions[ i ].length; ++d )
				mul *= mipmapResolutions[ i ][ d ];

			if ( mul > maxMul )
			{
				maxMul = mul;
				bestLevel = i;
			}
		}

		return bestLevel;
	}

	/**
	 * 
	 * @param imgLoader the imgloader
	 * @param vd the view description
	 * @param t - will be filled if downsampling is performed, otherwise identity transform
	 * @param downsampleXY - specify which downsampling ( 1,2,4,8 )
	 * @param downsampleZ - specify which downsampling ( 1,2,4,8 )
	 * @return opened image
	 */
	public static RandomAccessibleInterval< FloatType > openAndDownsample(
			final ImgLoader imgLoader,
			final ViewDescription vd,
			final AffineTransform3D t,
			final int downsampleXY,
			final int downsampleZ,
			final boolean openCompletely )
	{
		IOFunctions.println(
				"(" + new Date(System.currentTimeMillis()) + "): "
				+ "Requesting Img from ImgLoader (tp=" + vd.getTimePointId() + ", setup=" + vd.getViewSetupId() + ")" );

		if ( downsampleXY > 1 )
			IOFunctions.println( "(" + new Date( System.currentTimeMillis() )  + "): Downsampling in XY " + downsampleXY + "x ..." );

		if ( downsampleZ > 1 )
			IOFunctions.println( "(" + new Date( System.currentTimeMillis() )  + "): Downsampling in Z " + downsampleZ + "x ..." );

		int dsx = downsampleXY;
		int dsy = downsampleXY;
		int dsz = downsampleZ;

		RandomAccessibleInterval< net.imglib2.type.numeric.real.FloatType > input = null;

		if ( ( dsx > 1 || dsy > 1 || dsz > 1 ) && MultiResolutionImgLoader.class.isInstance( imgLoader ) )
		{
			MultiResolutionImgLoader mrImgLoader = ( MultiResolutionImgLoader ) imgLoader;

			double[][] mipmapResolutions = mrImgLoader.getSetupImgLoader( vd.getViewSetupId() ).getMipmapResolutions();

			int bestLevel = 0;
			for ( int level = 0; level < mipmapResolutions.length; ++level )
			{
				double[] factors = mipmapResolutions[ level ];
				
				// this fails if factors are not ints
				final int fx = (int)Math.round( factors[ 0 ] );
				final int fy = (int)Math.round( factors[ 1 ] );
				final int fz = (int)Math.round( factors[ 2 ] );
				
				if ( fx <= dsx && fy <= dsy && fz <= dsz && contains( fx, ds ) && contains( fy, ds ) && contains( fz, ds ) )
					bestLevel = level;
			}

			final int fx = (int)Math.round( mipmapResolutions[ bestLevel ][ 0 ] );
			final int fy = (int)Math.round( mipmapResolutions[ bestLevel ][ 1 ] );
			final int fz = (int)Math.round( mipmapResolutions[ bestLevel ][ 2 ] );

			t.set( mrImgLoader.getSetupImgLoader( vd.getViewSetupId() ).getMipmapTransforms()[ bestLevel ] );

			dsx /= fx;
			dsy /= fy;
			dsz /= fz;

			IOFunctions.println(
					"(" + new Date(System.currentTimeMillis()) + "): " +
					"Using precomputed Multiresolution Images [" + fx + "x" + fy + "x" + fz + "], " +
					"Remaining downsampling [" + dsx + "x" + dsy + "x" + dsz + "]" );

			if ( openCompletely )
				input = mrImgLoader.getSetupImgLoader( vd.getViewSetupId() ).getFloatImage( vd.getTimePointId(), bestLevel, false, LOAD_COMPLETELY );
			else
				input = mrImgLoader.getSetupImgLoader( vd.getViewSetupId() ).getFloatImage( vd.getTimePointId(), bestLevel, false );
		}
		else
		{
			if ( openCompletely )
				input = imgLoader.getSetupImgLoader( vd.getViewSetupId() ).getFloatImage( vd.getTimePointId(), false, LOAD_COMPLETELY );
			else
				input = imgLoader.getSetupImgLoader( vd.getViewSetupId() ).getFloatImage( vd.getTimePointId(), false );
			t.identity();
		}

		final ImgFactory< net.imglib2.type.numeric.real.FloatType > f = ((Img<net.imglib2.type.numeric.real.FloatType>)input).factory();

		// fix scaling
		t.set( t.get( 0, 0 ) * dsx, 0, 0 );
		t.set( t.get( 1, 1 ) * dsy, 1, 1 );
		t.set( t.get( 2, 2 ) * dsz, 2, 2 );

		// fix translation
		t.set( t.get( 0, 3 ) * dsx, 0, 3 );
		t.set( t.get( 1, 3 ) * dsy, 1, 3 );
		t.set( t.get( 2, 3 ) * dsz, 2, 3 );
		
		for ( ;dsx > 1; dsx /= 2 )
			input = Downsample.simple2x( input, f, new boolean[]{ true, false, false } );

		for ( ;dsy > 1; dsy /= 2 )
			input = Downsample.simple2x( input, f, new boolean[]{ false, true, false } );

		for ( ;dsz > 1; dsz /= 2 )
			input = Downsample.simple2x( input, f, new boolean[]{ false, false, true } );

		return input;
	}

	private static final boolean contains( final int i, final int[] values )
	{
		for ( final int j : values )
			if ( i == j )
				return true;

		return false;
	}
}
