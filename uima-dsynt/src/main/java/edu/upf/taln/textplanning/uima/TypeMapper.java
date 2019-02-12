package edu.upf.taln.textplanning.uima;

import java.util.HashMap;
import java.util.Map;

public class TypeMapper
{
	public enum Type
	{
		Thing, Location,
		Incident, Flood, Crack, Collapse, Overflow, Fire, Smoke, Empty, Full, TrafficJam,
		VulnerableObject, Asset, LivingBeing, Bridge, River, Square, Car, Vehicle, Person,
		Sewer, Levee, Forest, Park, Venue, Building
	}

	private final static Map<String, Type> meanings2types = new HashMap<String, Type>()
	{
		{

		}
	};

	// Used to guarantee backwards compatibility with the pilot of the first prototype
	private final static Map<String, Type> forms2types = new HashMap<String, Type>()
	{
		{
			// English
			put("flood", Type.Flood);
			put("floods", Type.Flood);
			put("flooded", Type.Flood);
			put("flooding", Type.Flood);
			put("crack", Type.Crack);
			put("cracks", Type.Crack);
			put("cracked", Type.Crack);
			put("cracking", Type.Crack);
			put("collapse", Type.Collapse);
			put("collapses", Type.Collapse);
			put("collapsed", Type.Collapse);
			put("collapsing", Type.Collapse);
			put("overflow", Type.Overflow);
			put("overflows", Type.Overflow);
			put("overflew", Type.Overflow);
			put("overflown", Type.Overflow);
			put("overflowing", Type.Overflow);
			put("fire", Type.Fire);
			put("fires", Type.Fire);
			put("smoke", Type.Smoke);
			put("empty", Type.Empty);
			put("empties", Type.Empty);
			put("emptied", Type.Empty);
			put("emptying", Type.Empty);
			put("full", Type.Full);
			put("fill", Type.Full);
			put("fills", Type.Full);
			put("filled", Type.Full);
			put("filling", Type.Full);
			put("jam", Type.TrafficJam);
			put("jams", Type.TrafficJam);
			put("congestion", Type.TrafficJam);
			put("congestions", Type.TrafficJam);
			put("bridge", Type.Bridge);
			put("bridges", Type.Bridge);
			put("river", Type.River);
			put("rivers", Type.River);
			put("square", Type.Square);
			put("squares", Type.Square);
			put("car", Type.Car);
			put("cars", Type.Car);
			put("vehicle", Type.Vehicle);
			put("vehicles", Type.Vehicle);
			put("human", Type.Person);
			put("humans", Type.Person);
			put("people", Type.Person);
			put("man", Type.Person);
			put("woman", Type.Person);
			put("men", Type.Person);
			put("women", Type.Person);
			put("sewer", Type.Sewer);
			put("sewers", Type.Sewer);
			put("levee", Type.Levee);
			put("levees", Type.Levee);
			put("forest", Type.Forest);
			put("woods", Type.Forest);
			put("park", Type.Park);
			put("parks", Type.Park);
			put("venue", Type.Venue);
			put("venues", Type.Venue);
			put("building", Type.Building);
			put("buildings", Type.Building);
			put("house", Type.Building);
			put("houses", Type.Building);
			put("basement", Type.Building);
			put("basements", Type.Building);

			// Italian
			put("piazza", Type.Square);
			put("allagata", Type.Flood);
			put("allagare", Type.Flood);
			put("alluvione", Type.Flood);
			put("allagamento", Type.Flood);
			put("floode", Type.Flood);
			put("fognatura", Type.Sewer);
			put("argine", Type.Levee);
			put("argini", Type.Levee);
			put("traboccare", Type.Overflow);
			put("traboccato", Type.Overflow);
			put("ponte", Type.Bridge);
			put("crepe", Type.Crack);
			put("crepa", Type.Crack);
			put("spaccare", Type.Crack);
			put("spaccati", Type.Crack);
			put("crollare", Type.Collapse);
			put("crollati", Type.Collapse);
			put("fiume", Type.River);

			// Spanish
			put("parque", Type.Park);
			put("incendio", Type.Fire);
			put("fuego", Type.Fire);
			put("bosque", Type.Forest);

			// Greek
			put("αίθουσα", Type.Venue); // feminine
			put("κτίριο", Type.Building); // neuter
			put("κτήριο", Type.Building); // neuter
			put("χώρος", Type.Venue); // masculine
			put("δημαρχείο", Type.Building); // city hall, neuter
			put("δημαρχείου", Type.Building); // city hall, neuter+possessive
			put("άδειος", Type.Empty); // masculine
			put("άδεια", Type.Empty); // feminine
			put("άδειο", Type.Empty); // neuter
			put("γεμάτο", Type.Full); // masculine
			put("γεμάτη", Type.Full); // feminine
			put("γεμάτος", Type.Full); // neuter
			put("ΚΑΠΗ", Type.Venue); // feminine
		}
	};


	public static String map(String meaning, String text, boolean isPredicate)
	{
		if (meaning != null && meanings2types.containsKey(meaning))
			return meanings2types.get(meaning).toString();

		if (forms2types.containsKey(text))
			return forms2types.get(text).toString();

		if (isPredicate)
			return Type.Incident.toString();
		else
			return Type.Thing.toString();
	}

	public static boolean isIncident(String type)
	{
		switch(Type.valueOf(type))
		{
			case Incident:
			case Flood:
			case Crack:
			case Collapse:
			case Overflow:
			case Fire:
			case Smoke:
			case Empty:
			case Full:
			case TrafficJam:
				return true;
			default:
				return false;
		}
	}

	public static boolean isVulnerableObject(String type)
	{
		switch(Type.valueOf(type))
		{
			case VulnerableObject:
			case Asset:
			case LivingBeing:
			case Bridge:
			case River:
			case Square:
			case Car:
			case Vehicle:
			case Person:
			case Sewer:
			case Levee:
			case Forest:
			case Park:
			case Venue:
			case Building:
				return true;
			default:
				return false;
		}
	}
}
