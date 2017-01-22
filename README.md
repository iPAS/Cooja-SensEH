SensEH plugin for Cooja was originally created by [Usman Raza](https://github.com/usmanraza/).
It provides a capability for nodes in the network to be able to harvest energy whilst they are operating, and, the energy profiles of them will be reported after the simulation.
Efficiency in the operation could be considered via the reported result.

Due to the requirement of my research,
  I provide a multiple transmission level recording function to the plugin;
  as a result, the adjusted transmission power in each node will be recorded.
The network operation under small-amount continual harvested energy can be assessed.

Hope you might find some source of information that's worth it to be used,
  and also thank you to the [author](https://github.com/usmanraza/) who founded the plugin.

Best regards,
[iPAS](ptiwatthanont@gmail.com)


# Features

...


# Installation

1. Copy the plugin into "[contiki]/tools/cooja/apps/"

```bash
$ git clone https://github.com/iPAS/Cooja-SensEH.git "senseh"
```

2. Edit build.xml in Cooja directory, "[contiki]/tools/cooja/",
    so that SensEH plugin will be considered everytime Cooja is built.
    Put some configurations in two sections, i.e.,

```xml
    <target name="clean" depends="init">
        ...
        <ant antfile="build.xml" dir="apps/senseh" target="clean" inheritAll="false"/>
        ...

    </target>

    ...

    <target name="jar" depends="jar_cooja">
        ...
        <ant antfile="build.xml" dir="apps/senseh" target="jar" inheritAll="false"/>
        ...

    </target>
```

3. Build and run Cooja (the plugin will be built automatically:

```bash
$ ant run
```


# Setup a CSC simulation file for Cooja and SensEH

xxx
