import de.jungblut.glove.impl.GloveBinaryWriter;
import de.jungblut.glove.util.WritableUtils;

import java.io.*;
import java.nio.file.Path;
import java.util.ArrayList;

// Reads keys from Glove random access files
public class GloveKeysReader
{
	private final ArrayList<String> keys = new ArrayList<>();
	private long size;

	public GloveKeysReader(Path gloveBinaryFolder)
			throws IOException
	{
		Path dict = gloveBinaryFolder.resolve(GloveBinaryWriter.DICT_FILE);

		try (DataInputStream in = new DataInputStream(new BufferedInputStream(
				new FileInputStream(dict.toFile()))))
		{

			long lastBlock = -1;
			size = -1;
			while (true)
			{
				String s = in.readUTF();
				long off = WritableUtils.readVLong(in);

				if (lastBlock == -1)
				{
					lastBlock = off;
				}
				else
				{
					if (size == -1)
					{
						size = off;
					}
					if (off - lastBlock != size)
					{
						throw new IOException(
								"Dictionary is corrupted, blocking isn't exact. Expected blocks of "
										+ size);
					}

					lastBlock = off;
				}

				keys.add(s);
			}
		}
		catch (EOFException e)
		{
			// expected
		}
	}

	public int getNumKeys() { return (int)size;}
	public String get(int i) { return keys.get(i); }
}
