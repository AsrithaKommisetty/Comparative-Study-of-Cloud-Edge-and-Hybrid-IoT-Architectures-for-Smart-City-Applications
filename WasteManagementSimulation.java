package org.fog.test.perfeval;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.power.PowerHost;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;
import org.cloudbus.cloudsim.sdn.overbooking.BwProvisionerOverbooking;
import org.cloudbus.cloudsim.sdn.overbooking.PeProvisionerOverbooking;

import org.fog.application.AppEdge;
import org.fog.application.AppLoop;
import org.fog.application.Application;
import org.fog.application.selectivity.FractionalSelectivity;

import org.fog.entities.FogBroker;
import org.fog.entities.FogDevice;
import org.fog.entities.FogDeviceCharacteristics;
import org.fog.entities.Sensor;
import org.fog.entities.Tuple;

import org.fog.placement.Controller;
import org.fog.placement.ModuleMapping;
import org.fog.placement.ModulePlacementEdgewards;

import org.fog.policy.AppModuleAllocationPolicy;
import org.fog.scheduler.StreamOperatorScheduler;

import org.fog.utils.FogLinearPowerModel;
import org.fog.utils.FogUtils;
import org.fog.utils.TimeKeeper;
import org.fog.utils.distribution.DeterministicDistribution;

public class WasteManagementSimulation {

    static List<FogDevice> fogDevices = new ArrayList<>();
    static List<Sensor> sensors = new ArrayList<>();

    static int numOfAreas = 1;
    static int numOfBinsPerArea = 200;

    public static void main(String[] args) {

        Log.printLine("Starting Waste Management Simulation...");

        try {

            Log.disable();

            int num_user = 1;
            Calendar calendar = Calendar.getInstance();
            boolean trace_flag = false;

            CloudSim.init(num_user, calendar, trace_flag);

            String appId = "waste_management";

            FogBroker broker = new FogBroker("broker");

            Application application = createApplication(appId, broker.getId());
            application.setUserId(broker.getId());

            createFogDevices(broker.getId(), appId);

            ModuleMapping moduleMapping = ModuleMapping.createModuleMapping();

            moduleMapping.addModuleToDevice("waste_detector", "d-0");
            moduleMapping.addModuleToDevice("waste_analyzer", "cloud");
            moduleMapping.addModuleToDevice("user_interface", "cloud");

            Controller controller = new Controller("master-controller",
                    fogDevices, sensors, new ArrayList<>());

            controller.submitApplication(application,
                    new ModulePlacementEdgewards(
                            fogDevices,
                            sensors,
                            new ArrayList<>(),
                            application,
                            moduleMapping));

            TimeKeeper.getInstance().setSimulationStartTime(
                    Calendar.getInstance().getTimeInMillis());

            CloudSim.startSimulation();

            CloudSim.stopSimulation();

            Log.printLine("Waste Management Simulation finished!");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void createFogDevices(int userId, String appId) {

        FogDevice cloud = createFogDevice(
                "cloud",
                44800,
                40000,
                100,
                10000,
                0,
                0.01,
                16 * 103,
                16 * 83.25);

        cloud.setParentId(-1);
        fogDevices.add(cloud);

        FogDevice proxy = createFogDevice(
                "proxy-server",
                2800,
                4000,
                10000,
                10000,
                1,
                0.0,
                107.339,
                83.4333);

        proxy.setParentId(cloud.getId());
        proxy.setUplinkLatency(100);
        fogDevices.add(proxy);

        for (int i = 0; i < numOfAreas; i++) {
            addArea(i + "", userId, appId, proxy.getId());
        }
    }

    public static FogDevice addArea(String id, int userId, String appId, int parentId) {

        FogDevice router = createFogDevice(
                "d-" + id,
                2800,
                4000,
                10000,
                10000,
                1,
                0.0,
                107.339,
                83.4333);

        fogDevices.add(router);
        router.setUplinkLatency(2);

        for (int i = 0; i < numOfBinsPerArea; i++) {

            String binId = id + "-" + i;

            Sensor sensor = new Sensor(
                    "bin-" + binId,
                    "WASTE_SENSOR",
                    userId,
                    appId,
                    new DeterministicDistribution(10));

            sensor.setGatewayDeviceId(router.getId());
            sensor.setLatency(1.0);

            sensors.add(sensor);
        }

        router.setParentId(parentId);

        return router;
    }

    public static FogDevice createFogDevice(
            String nodeName,
            long mips,
            int ram,
            long upBw,
            long downBw,
            int level,
            double ratePerMips,
            double busyPower,
            double idlePower) {

        List<Pe> peList = new ArrayList<>();

        peList.add(new Pe(0, new PeProvisionerOverbooking(mips)));

        int hostId = FogUtils.generateEntityId();
        long storage = 1000000;
        int bw = 10000;

        PowerHost host = new PowerHost(
                hostId,
                new RamProvisionerSimple(ram),
                new BwProvisionerOverbooking(bw),
                storage,
                peList,
                new StreamOperatorScheduler(peList),
                new FogLinearPowerModel(busyPower, idlePower));

        List<Host> hostList = new ArrayList<>();
        hostList.add(host);

        String arch = "x86";
        String os = "Linux";
        String vmm = "Xen";

        double time_zone = 10.0;
        double cost = 3.0;
        double costPerMem = 0.05;
        double costPerStorage = 0.001;
        double costPerBw = 0.0;

        LinkedList<Storage> storageList = new LinkedList<>();

        FogDeviceCharacteristics characteristics =
                new FogDeviceCharacteristics(
                        arch,
                        os,
                        vmm,
                        host,
                        time_zone,
                        cost,
                        costPerMem,
                        costPerStorage,
                        costPerBw);

        FogDevice fogdevice = null;

        try {
            fogdevice = new FogDevice(
                    nodeName,
                    characteristics,
                    new AppModuleAllocationPolicy(hostList),
                    storageList,
                    10,
                    upBw,
                    downBw,
                    0,
                    ratePerMips);
        } catch (Exception e) {
            e.printStackTrace();
        }

        fogdevice.setLevel(level);

        return fogdevice;
    }

    public static Application createApplication(String appId, int userId) {

        Application application = Application.createApplication(appId, userId);

        application.addAppModule("waste_detector", 10);
        application.addAppModule("waste_analyzer", 10);
        application.addAppModule("user_interface", 10);

        application.addAppEdge(
                "WASTE_SENSOR",
                "waste_detector",
                1000,
                2000,
                "WASTE_DATA",
                Tuple.UP,
                AppEdge.SENSOR);

        application.addAppEdge(
                "waste_detector",
                "waste_analyzer",
                2000,
                2000,
                "PROCESSED_DATA",
                Tuple.UP,
                AppEdge.MODULE);

        application.addAppEdge(
                "waste_analyzer",
                "user_interface",
                500,
                500,
                "BIN_STATUS",
                Tuple.UP,
                AppEdge.MODULE);

        application.addTupleMapping(
                "waste_detector",
                "WASTE_DATA",
                "PROCESSED_DATA",
                new FractionalSelectivity(1.0));

        application.addTupleMapping(
                "waste_analyzer",
                "PROCESSED_DATA",
                "BIN_STATUS",
                new FractionalSelectivity(1.0));

        final AppLoop loop1 = new AppLoop(
                new ArrayList<String>() {
                    {
                        add("waste_detector");
                        add("waste_analyzer");
                    }
                });

        List<AppLoop> loops =
                new ArrayList<AppLoop>() {
                    {
                        add(loop1);
                    }
                };

        application.setLoops(loops);

        return application;
    }
}