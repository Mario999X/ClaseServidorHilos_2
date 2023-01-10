package server

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import models.Alumno
import models.Usuario
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

        cliente.close()
    }

    private fun consultarAlumnos() {
        log.debug { "Consultando alumnos" }

        val response = Response(aulaDb.getAll().values.toList().toString(), Response.Type.OK)
        salida.writeUTF(json.encodeToString(response) + "\n")
    }

    private fun eliminarAlumno(request: Request<Alumno>) {
        log.debug { "Eliminando alumno" }
        val response: Response<String>

        val permisos = comprobarToken(request)

        response = if (!permisos) {
            log.debug { "No tiene permisos para esta operacion" }

            Response("Operacion NO Realizada, no tiene permisos", Response.Type.ERROR)
        } else {
            if (request.content?.let { aulaDb.delete(request.content.id) } == true) {
                Response("Operacion Realizada", Response.Type.OK)
            } else {
                Response("Operacion NO Realizada, alumno no existe", Response.Type.ERROR)
            }
        }
        salida.writeUTF(json.encodeToString(response) + "\n")
    }

    private fun modificarAlumnos(request: Request<Alumno>) {
        log.debug { "Procesando alumno" }
        val permisos = comprobarToken(request)

        if (!permisos) {
            log.debug { "No tiene permisos para esta operacion" }

            val response = Response("Operacion NO Realizada, no tiene permisos", Response.Type.ERROR)
            salida.writeUTF(json.encodeToString(response) + "\n")
        } else {
            request.content?.let { aulaDb.add(it) }

            val response = Response("Operacion Realizada", Response.Type.OK)
            salida.writeUTF(json.encodeToString(response) + "\n")
        }

    }

    private fun comprobarToken(request: Request<Alumno>): Boolean {
        log.debug { "Comprobando token..." }

        var funcionDisponible = true

        val token = request.token?.let { ManejadorTokens.decodeToken(it) }
        //println(token?.getClaim("rol"))
        //println(Usuario.TipoUser.USER.rol)

        if (token?.getClaim("rol").toString().contains(Usuario.TipoUser.USER.rol)) {
            funcionDisponible = false
        }

        return funcionDisponible
    }

    private fun enviarToken(request: Request<Alumno>) {
        log.debug { "Procesando token..." }

        val user = usersDb.login(request.content!!.nombre, request.content2!!.nombre)

        val responseToken = if (user == null) {
            println("User not found")
            Response(null, Response.Type.ERROR)
        } else {

            val token = ManejadorTokens.createToken(user.rol!!.rol)
            Response(token, Response.Type.OK)
        }

        salida.writeUTF(json.encodeToString(responseToken) + "\n")
    }

    private fun lecturaRequest(): Request<Alumno> {
        log.debug { "Procesando request..." }
        return json.decodeFromString(entrada.readUTF())
    }
}