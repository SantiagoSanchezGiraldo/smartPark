# Avance del desarrollo del frontend – SmartPark

## 1. Descripción del avance

Como parte del desarrollo de **SmartPark**, hemos trabajado en una primera versión del frontend, es decir, la interfaz visual desde la que se podrá consultar el estado del parqueadero inteligente. Nuestro propósito en esta etapa fue organizar la información que recibirá el usuario y definir cómo se presentarán las principales funciones del sistema.

Para construir este prototipo utilizamos **HTML y CSS**. Con HTML estructuramos las secciones de la página y con CSS definimos su presentación, incluyendo colores, íconos, distribución de los elementos y adaptación a pantallas de diferentes tamaños. Por ahora se trata de una interfaz web con apariencia de aplicación móvil, no de una aplicación nativa desarrollada en Android Studio.

## 2. Funcionalidades representadas en la interfaz

Organizamos el frontend en tres secciones principales:

- **Disponibilidad de espacios:** incluimos tres puestos de parqueo (P1, P2 y P3), junto con un resumen de espacios libres y ocupados. Actualmente se muestran dos disponibles y uno ocupado como ejemplo.
- **Lecturas del sistema:** diseñamos un espacio para visualizar las distancias medidas por los sensores de cada puesto y la detección de proximidad en el acceso.
- **Estado de los componentes:** agregamos indicadores para las luces LED, el sensor de proximidad, el servomotor y las lecturas de los tres sensores.

También diferenciamos visualmente los estados libres y ocupados mediante colores, con el fin de facilitar la interpretación de la información.

Es importante aclarar que **los valores presentados son datos de muestra**. La interfaz todavía no recibe información de Arduino ni actualiza automáticamente los estados del parqueadero.

## 3. Relación con los objetivos del PIA

En la propuesta inicial planteamos el desarrollo de un prototipo funcional de SmartPark, acompañado de un modelo a escala que permita simular el monitoreo y control de un parqueadero inteligente.

Con el trabajo realizado hasta ahora, **hemos avanzado en el objetivo específico de diseñar un prototipo de la aplicación para el manejo y visualización de datos**. La interfaz nos permitió establecer qué información queremos presentar y cómo puede organizarse para que el usuario la consulte de manera sencilla.

Asimismo, el diseño contempla algunos de los componentes definidos en nuestra propuesta, como los sensores de proximidad, las luces LED y los servomotores. Sin embargo, esto corresponde únicamente a su representación visual y no implica que ya estén integrados o funcionando desde la aplicación.

Otros elementos incluidos en el PIA, como el **techo inteligente controlado mediante un sensor de humedad**, el registro de ingreso de vehículos y la comunicación con el sistema físico, todavía no están implementados en el frontend actual.

## 4. Próximos pasos

Para continuar con el proyecto, tenemos previsto:

1. **Verificar el funcionamiento de los sensores y demás componentes físicos** que se utilizarán en el modelo a escala.
2. **Definir e implementar la comunicación entre Arduino y la aplicación.** En nuestra propuesta contemplamos el uso de un módulo Bluetooth y también mencionamos la transmisión de datos a la nube, por lo que debemos precisar cómo se integrarán estas tecnologías.
3. **Conectar las lecturas reales con la interfaz**, de modo que la disponibilidad de los puestos y los estados de los componentes puedan actualizarse según lo que detecten los sensores.
4. **Incorporar las funciones pendientes de la propuesta**, especialmente la detección de humedad, el control del techo inteligente y la información sobre el ingreso de vehículos.
5. **Definir el desarrollo de la aplicación final**, teniendo en cuenta que el PIA plantea utilizar Android Studio con Java, mientras que el prototipo visual actual fue realizado con HTML y CSS.
6. **Realizar pruebas de integración y funcionamiento** entre la maqueta, los sensores y la aplicación, para identificar errores y realizar los ajustes necesarios.

## 5. Conclusión

Hasta el momento, nuestro principal avance ha sido la creación de una primera propuesta visual para SmartPark. Esta etapa nos permitió organizar los datos que queremos mostrar, representar el estado de los espacios y componentes del parqueadero y establecer una base para el desarrollo posterior.

Aunque todavía no contamos con una aplicación conectada al sistema físico, consideramos que el frontend desarrollado constituye un avance concreto frente a los objetivos de diseño establecidos en el PIA. Nuestro siguiente propósito es integrar el hardware y el software para que la información deje de ser simulada y el proyecto avance hacia un prototipo funcional.
