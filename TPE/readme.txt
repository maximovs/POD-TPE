Preprocesamiento de señales
---------------------------

Construccion
------------

Ejecutar: mvn package assembly:single
En la carpeta target se creara el jar TPE-1.0-jar-with-dependencies.jar con todas las clases.

Para ejecutar: java -jar -Xmx2g -Djgroups.bind_addr=<IP> -Djava.net.preferIPv4Stack=true TPE/target/TPE-1.0-jar-with-dependencies.jar <port> <cores>

donde <IP> es la dirección de red desde donde se conecta a la red con el resto de los nodos.

Clases y paquetes destacables
-----------------------------

Toda la interfaz publica se encuentra en el paquete ar.edu.itba.pod.api
Las dos interfases más importantes son SignalProcesor y SPNode.

En ar.edu.itba.pod.legajo51071.impl se encuentra una implementación de SignalProcessor y SPNode: ClusterSignalProcessor. Esta clase tiene un
LocalProcessor donde guarda las señales propias y las procesa cuando llegan requerimientos. Además contiene un ClusterProcessor que se encarga de 
distribuir las señales (por medio del ClusterBalancer que se encarga de distribuir los backups y reenvíos y además determina el fin del estado degradado) y
de pedir al resto de los nodos que procesen las señales que recibe para procesar. 

En los directorios de test (src/test/java) se encuentran, ademas de algunos casos de prueba 
unitarios:
SampleClient y SampleServer, que muestran el uso de las interfases a través de RMI.
SideBySideTester, que permite comparar una implementacion contra la de referencia para
verificar que la funcionalidad permanece intacta. Ver un ejemplo rudimentario

