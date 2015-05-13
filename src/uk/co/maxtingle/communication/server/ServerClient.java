package uk.co.maxtingle.communication.server;

import uk.co.maxtingle.communication.common.AuthState;
import uk.co.maxtingle.communication.common.BaseClient;
import uk.co.maxtingle.communication.common.Message;
import uk.co.maxtingle.communication.debug.Debugger;

import java.net.Socket;
import java.util.Map;

public class ServerClient extends BaseClient
{
    private Server _server;

    public ServerClient(Socket socket, Server server) throws Exception {
        this.connect(socket);
        this._server = server;

        /* Pre-auth */
        if(this._server._options.useMagic) {
            this.setAuthState(AuthState.AWAITING_MAGIC);
            this.sendMessage(new Message(ServerOptions.REQUEST_MAGIC));
        }
        else if(this._server._options.useCredentials) {
            this.setAuthState(AuthState.AWAITING_CREDENTIALS);
            this.sendMessage(new Message(ServerOptions.REQUEST_CREDENTIALS));
        }
        else {
            this.setAuthState(AuthState.ACCEPTED); //no auth in place
            this.sendMessage(new Message(ServerOptions.ACCEPTED_AUTH));
        }
    }

    @Override
    public void disconnect() {
        super.disconnect();

        if(this._server._clients.indexOf(this) == -1) {
            Debugger.log("Server", "WARNING: Client disconnected but not in clients list");
        }
        else {
            this._server._clients.remove(this);
        }
    }

    public void handleAuthMessage(Message message) throws Exception {
        if(message.params.length != 1) { //not got any params
            throw new Exception("Incorrect number of params");
        }
        else if(message.params[0] == null) {
            throw new Exception("Null param given");
        }
        else if(this._server._options.useMagic && this.getAuthState() == AuthState.AWAITING_MAGIC) {
            if(!(message.params[0] instanceof String)) {
                //not sent a string
                throw new Exception("Incorrect type of param");
            }
            else if(this._server._options.magicAuthHandler.authMagic((String)message.params[0], this, message, this._server._options)) {
                if(this._server._options.useCredentials) { //stage 1 complete, ask for credentials now
                    this.setAuthState(AuthState.AWAITING_CREDENTIALS);
                    message.respond(new Message(true, ServerOptions.REQUEST_CREDENTIALS));
                }
                else { //expectedMagic only, they have passed auth
                    this.setAuthState(AuthState.ACCEPTED);
                    message.respond(new Message(true, ServerOptions.ACCEPTED_AUTH));
                }
            }
            else {
                throw new Exception("Incorrect magic");
            }
        }
        else if(this._server._options.useCredentials && this.getAuthState() == AuthState.AWAITING_CREDENTIALS) {
            String username;
            String password;

            try {
                if(!(message.params[0] instanceof Map)) {
                    throw new Exception("Invalid username and password type. params[0] must be an associative array (Map) with username and password in.");
                }

                @SuppressWarnings("unchecked")
                Map<String, String> hashMap = (Map<String, String>) message.params[0];

                username = hashMap.get("username");
                password = hashMap.get("password");
            }
            catch(Exception e) {
                throw new Exception("Invalid username and password type. params[0] must be an associative array (Map) with username and password in.");
            }

            if(this._server._options.credentialAuthHandler.authCredentials(username, password, this, message, this._server._options)) {
                this.setAuthState(AuthState.ACCEPTED);
                message.respond(new Message(true, ServerOptions.ACCEPTED_AUTH));
            }
            else {
                throw new Exception("Invalid credentials");
            }
        }
    }
}