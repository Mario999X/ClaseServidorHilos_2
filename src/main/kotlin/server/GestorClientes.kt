package server

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import models.Alumno
import models.mensajes.Request
import models.mensajes.Response
import monitor.AulaDb
import monitor.UsersDb
import mu.KotlinLogging
import security.ManejadorTokens
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket

private val log = KotlinLogging.logger {}
private val json = Json

class GestorClientes(private val cliente: Socket, private val usersDb: UsersDb, private val aulaDb: AulaDb) : Runnable {

    // Preparamos los canales de entrada-salida
    private val salida = DataOutputStream(cliente.getOutputStream())
    private val entrada = DataInputStream(cliente.getInputStream())

    override fun run() {
        val request = lecturaRequest() // Leemos el request

        when (request.type) {
            Request.Type.GET_TOKEN -> {
                enviarToken(request)
            }

            Request.Type.ADD -> {
                modificarAlumnos(request)
            }

            Request.Type.DELETE -> {
                eliminarAlumno(request)
            }

            Request.Type.CONSULT -> {
                consultarAlumnos()
            }

            else -> {}
        }
    }

    private fun consultarAlumnos() {
        log.debug { "Consultando alumnos" }

        val response = Response(aulaDb.getAll().values.toList().toString(), Response.Type.OK)
        salida.writeUTF(json.encodeToString(response) + "\n")
    }

    private fun eliminarAlumno(request: Request<Alumno>) {
        log.debug { "Eliminando alumno" }

        if (request.content?.let { aulaDb.delete(request.content.id) } == true) {
            val response = Response("Operacion Realizada", Response.Type.OK)
            salida.writeUTF(json.encodeToString(response) + "\n")
        } else {
            val response = Response("Operacion NO Realizada, alumno no existe", Response.Type.ERROR)
            salida.writeUTF(json.encodeToString(response) + "\n")
        }
    }

    private fun modificarAlumnos(request: Request<Alumno>) {
        log.debug { "Procesando alumno" }

        request.content?.let { aulaDb.add(it) }

        val response = Response("Operacion Realizada", Response.Type.OK)
        salida.writeUTF(json.encodeToString(response) + "\n")
    }

    private fun enviarToken(request: Request<Alumno>) {
        log.debug { "Procesando token..." }

        val responseToken: Response<String> = if (request.type == Request.Type.GET_TOKEN && request.token == null) {

            val user = usersDb.login(request.content!!.nombre, request.content2!!.nombre)

            if (user == null) {

                println("User not found")
                Response(null, Response.Type.ERROR)
            } else {

                val token = user.rol?.let { ManejadorTokens.createToken(it.rol) }
                Response(token, Response.Type.OK)
            }

        } else {
            request.token?.let { ManejadorTokens.decodeToken(it) }
            log.debug { "Este cliente ya posee un token y se comprobo" }
            Response(null, Response.Type.OK)
        }

        salida.writeUTF(json.encodeToString(responseToken) + "\n")
    }

    private fun lecturaRequest(): Request<Alumno> {
        log.debug { "Procesando request..." }
        return json.decodeFromString(entrada.readUTF())
    }
}