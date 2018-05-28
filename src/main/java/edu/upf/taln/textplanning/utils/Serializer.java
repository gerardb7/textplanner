package edu.upf.taln.textplanning.utils;

import java.io.*;
import java.nio.file.Path;

// Code taken from https://www.journaldev.com/2452/serialization-in-java
public class Serializer
{
	// utility class to recover serialized objects which have been moved to a different package
//	private static class RecoverStream extends ObjectInputStream
//	{
//
//		public RecoverStream(InputStream in) throws IOException { super(in); }
//
//		@Override
//		protected java.io.ObjectStreamClass readClassDescriptor()
//				throws IOException, ClassNotFoundException
//		{
//			ObjectStreamClass desc = super.readClassDescriptor();
//			switch (desc.getName())
//			{
//				case "edu.upf.taln.textplanning.structures.amr.GraphList":
//					return ObjectStreamClass.lookup(edu.upf.taln.textplanning.input.amr.GraphList.class);
//				case "edu.upf.taln.textplanning.structures.amr.Candidate":
//					return ObjectStreamClass.lookup(edu.upf.taln.textplanning.input.amr.Candidate.class);
//				case "edu.upf.taln.textplanning.structures.amr.Candidate$Type":
//					return ObjectStreamClass.lookup(edu.upf.taln.textplanning.input.amr.Candidate.Type.class);
//				case "edu.upf.taln.textplanning.structures.amr.CoreferenceChain":
//					return ObjectStreamClass.lookup(edu.upf.taln.textplanning.input.amr.CoreferenceChain.class);
//				case "edu.upf.taln.textplanning.structures.amr.SemanticGraph":
//					return ObjectStreamClass.lookup(edu.upf.taln.textplanning.input.amr.SemanticGraph.class);
//			}
//			return desc;
//		}
//	}


	// deserialize to Object from given file
	public static Object deserialize(Path input) throws IOException,
			ClassNotFoundException
	{
		FileInputStream fis = new FileInputStream(input.toString());
		// RecoverStream ois = new RecoverStream(fis);
		ObjectInputStream ois = new ObjectInputStream(fis);
		Object obj = ois.readObject();
		ois.close();
		return obj;
	}

	// serialize the given object and save it to file
	public static void serialize(Object obj, Path output)
			throws IOException
	{
		FileOutputStream fos = new FileOutputStream(output.toString());
		ObjectOutputStream oos = new ObjectOutputStream(fos);
		oos.writeObject(obj);

		fos.close();
	}
}