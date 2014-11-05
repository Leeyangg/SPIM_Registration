package spim.fiji.plugin.resave;

import bdv.export.ProgressWriter;
import ij.IJ;
import ij.io.LogStream;

import java.io.PrintStream;

public class ProgressWriterIJ implements ProgressWriter
{
	protected final PrintStream out;

	protected final PrintStream err;

	public ProgressWriterIJ()
	{
		out = new LogStream();
		err = new LogStream();
	}

	@Override
	public PrintStream out()
	{
		return out;
	}

	@Override
	public PrintStream err()
	{
		return err;
	}

	@Override
	public void setProgress( final double completionRatio )
	{
		IJ.showProgress( completionRatio );
	}
}