package org.mitre.bch.cath.simulation.utils;

import org.mitre.bch.cath.simulation.entity.Lab;
import org.mitre.bch.cath.simulation.entity.Procedure;
import org.mitre.bch.cath.simulation.entity.Resource;

import java.util.HashMap;
import java.util.Map;

/** Entity manager
 * Contains list of labs, resources, procedures
 * @author H. Haven Liu, The MITRE Corporation
 */


public class EntityManager {
    /** A Map that maps every instance of the Lab object to its ID */
    public final Map<Integer, Lab> labMap = new HashMap<>();
    /** A Map that maps resource types to its name */
    public final Map<String, Resource> resourceMap = new HashMap<>();

    /** A Map that maps every instance of the Procedure object to its ID */
    public final Map<Integer, Procedure> procedureMap = new HashMap<>();

    public EntityManager() {
    }
}
