package client

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import models.Alumno
import models.mensajes.Request
import models.mensajes.Response
import mu.KotlinLogging
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetAddress
import java.net.Socket

private val log = KotlinLogging.logger {}
private val json = Json

// Informacion del cliente y la conexion a realizar
private lateinit var direccion: InetAddress
private lateinit var servidor: Socket
private const val PUERTO = 6969

private lateinit var request: Request<Alumno> // Todas las request seran de este tipo, la dejamos como lateinit para usarla en los metodos

private var salida: Boolean = false

var token: String? =
    null // Se actualizara mientras el cliente este funcionando, y funcionara hasta la fecha limite.

fun main() {
    /*
    * Siempre que se inicie la aplicacion el usuario no estara "con un usuario" por lo que se pide por consola
    * su nombre, y su password, esta se verificara en el gestor y este le enviara el correspondiente mensaje, de error o
    * de confirmacion, con el token.
    *
    * El programa funcionara con conexion tipo HTTP, es decir, se conecta momentaneamente. La App se cerrara cuando escoja el usuario.
    * */

    while (!salida) {
        if (token == null) {
            token = solicitarToken()
        } else {
            solicitud()
        }
    }
}

private fun solicitud() {
    // Preparamos el menu principal del usuario, pediremos la opcion que quiera el usuario por consola, preparamos el request
    println(
        """
        1. AGREGAR ALUMNO
        2. BORRAR ALUMNO
        3. ACTUALIZAR ALUMNO
        4. CONSULTAR ALUMNOS
        5. SALIR
    """.trimIndent()
    )
    val opcion = readln().toIntOrNull()

    when (opcion) {
        1 -> {
            log.debug { "\tIntroduzca el NOMBRE del alumno: " }
            val nombre = readln()

            log.debug { "\tIntroduzca la NOTA SIN DECIMALES del alumno: " }
            val nota = readln().toInt()

            val alumno = Alumno(nombre, nota)
            request = Request(token, alumno, null, Request.Type.ADD)
        }

        2 -> {
            log.debug { "\tIntroduzca el ID del alumno a eliminar: " }
            val id = readln().toInt()

            val alumno = Alumno("", 0, id)
            request = Request(token, alumno, null, Request.Type.DELETE)
        }

        3 -> {
            log.debug { "\tIntroduzca el ID del alumno a actualizar: " }
            val id = readln().toInt()
            log.debug { "\tIntroduzca el NOMBRE nuevo del alumno: " }
            val nombre = readln()
            log.debug { "\tIntroduzca la NOTA nueva del alumno: " }
            val nota = readln().toInt()

            val alumno = Alumno(nombre, nota, id)
            request = Request(token, alumno, null, Request.Type.UPDATE)
        }

        4 -> {
            log.debug { "\tConsultando Alumnos" }

            request = Request(token, null, null, Request.Type.CONSULT)
        }

        5 -> {
            log.debug { "\tCerrando aplicacion" }
        }
    }
    if (opcion == 5) {
        salida = true
    } else {
        // Preparamos la conexion con el servidor
        direccion = InetAddress.getLocalHost()
        servidor = Socket(direccion, PUERTO)

        // Canales de entrada-salida
        val sendRequest = DataOutputStream(servidor.getOutputStream())
        val receiveResponse = DataInputStream(servidor.getInputStream())

        // Enviamos la peticion y esperamos la respuesta
        sendRequest.writeUTF(json.encodeToString(request) + "\n")
        log.debug { "Enviado $request" }

        val responseSolicitud: Response<String> = json.decodeFromString(receiveResponse.readUTF())

        val response = responseSolicitud.content
        log.debug { "Respuesta del servidor: $response" }

        if (responseSolicitud.type == Response.Type.TOKEN_EXPIRED) token = null
    }

}

private fun solicitarToken(): String? {
    // Solicitamos el usuario y la password
    println("Introduzca su nombre de usuario: ")
    val data1 = readln()
    println("Introduzca su contrase??a: ")
    val data2 = readln()

    // Preparamos el posible token que nos devolvera el servidor
    val response: String?

    /*
    Preparamos la conexion con el servidor una vez tengamos los datos, como todos los request van a ser del tipo Alumno
    tenemos que jugar con la forma de enviar los datos del usuario
     */
    direccion = InetAddress.getLocalHost()
    servidor = Socket(direccion, PUERTO)

    // Canales de entrada-salida
    val sendRequest = DataOutputStream(servidor.getOutputStream())
    val receiveResponse = DataInputStream(servidor.getInputStream())

    // cutre, yep.
    request = Request(null, Alumno(data1), data2, Request.Type.GET_TOKEN)
    sendRequest.writeUTF(json.encodeToString(request) + "\n")
    log.debug { "Se envio $request" }

    // Esperamos la respuesta del servidor y actuamos en consecuencia
    val tokenResponse: Response<String> = json.decodeFromString(receiveResponse.readUTF())

    response = if (tokenResponse.content != null) {
        log.debug { "\tSe recibio token: ${tokenResponse.content}" }
        tokenResponse.content
    } else {
        log.debug { "\tEl usuario no existe" }
        null
    }

    return response
}