package org.fog.test.perfeval;

import java.util.*;

import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.Log;

import org.fog.application.*;
import org.fog.application.selectivity.FractionalSelectivity;

import org.fog.entities.*;

import org.fog.placement.*;

import org.fog.utils.TimeKeeper;
import org.fog.utils.distribution.DeterministicDistribution;

public class WasteManagementCloud {

    static List<FogDevice> fogDevices = new ArrayList<>();
    static List<Sensor> sensors = new ArrayList<>();

    static int numOfBinsPerArea = 200;

    public static void main(String[] args) {

        Log.printLine("Starting Cloud Waste Management Simulation...");

        try {

            int num_user = 1;
            Calendar calendar = Calendar.getInstance();
            boolean trace_flag = false;
            Log.disable();
            CloudSim.init(num_user, calendar, trace_flag);

            String appId = "waste_cloud";

            FogBroker broker = new FogBroker("broker");

            createFogDevices(broker.getId(), appId);

            Application application = createApplication(appId, broker.getId());
            application.setUserId(broker.getId());

            ModuleMapping moduleMapping = ModuleMapping.createModuleMapping();

            moduleMapping.addModuleToDevice("waste_detector", "cloud");
            moduleMapping.addModuleToDevice("waste_analyzer", "cloud");
            moduleMapping.addModuleToDevice("user_interface", "cloud");

            Controller controller = new Controller(
                    "master-controller",
                    fogDevices,
                    sensors,
                    new ArrayList<>());

            controller.submitApplication(
                    application,
                    new ModulePlacementMapping(
                            fogDevices,
                            application,
                            moduleMapping));

            TimeKeeper.getInstance().setSimulationStartTime(
                    Calendar.getInstance().getTimeInMillis());

            CloudSim.startSimulation();
            CloudSim.stopSimulation();

            Log.printLine("Cloud Simulation finished!");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void createFogDevices(int userId, String appId) {

        FogDevice cloud = WasteManagementSimulation.createFogDevice(
                "cloud", 44800, 40000, 100, 10000, 0, 0.01, 16*103, 16*83.25);

        cloud.setParentId(-1);
        fogDevices.add(cloud);

        FogDevice router = WasteManagementSimulation.createFogDevice(
                "d-0", 2800, 4000, 10000, 10000, 1, 0.0, 107.339, 83.4333);

        router.setParentId(cloud.getId());
        router.setUplinkLatency(2);
        fogDevices.add(router);

        for(int i=0;i<numOfBinsPerArea;i++){

            Sensor sensor = new Sensor(
                    "bin-"+i,
                    "WASTE_SENSOR",
                    userId,
                    appId,
                    new DeterministicDistribution(10));

            sensor.setGatewayDeviceId(router.getId());
            sensor.setLatency(1.0);

            sensors.add(sensor);
        }
    }

    private static Application createApplication(String appId, int userId){

        Application application = Application.createApplication(appId, userId);

        application.addAppModule("waste_detector", 10);
        application.addAppModule("waste_analyzer", 10);
        application.addAppModule("user_interface", 10);

        application.addAppEdge("WASTE_SENSOR","waste_detector",1000,2000,"WASTE_DATA",Tuple.UP,AppEdge.SENSOR);

        application.addAppEdge("waste_detector","waste_analyzer",2000,2000,"PROCESSED_DATA",Tuple.UP,AppEdge.MODULE);

        application.addAppEdge("waste_analyzer","user_interface",500,500,"BIN_STATUS",Tuple.UP,AppEdge.MODULE);

        application.addTupleMapping("waste_detector","WASTE_DATA","PROCESSED_DATA",new FractionalSelectivity(1.0));

        application.addTupleMapping("waste_analyzer","PROCESSED_DATA","BIN_STATUS",new FractionalSelectivity(1.0));

        final AppLoop loop1 = new AppLoop(new ArrayList<String>(){{
            add("waste_detector");
            add("waste_analyzer");
        }});

        List<AppLoop> loops = new ArrayList<AppLoop>(){{add(loop1);}};

        application.setLoops(loops);

        return application;
    }
}