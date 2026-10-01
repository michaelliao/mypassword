using System;
using System.IO;
using System.Net;
using System.Text;
using System.Threading.Tasks;
using MyPasswordDesktop.Rpc;
using MyPasswordDesktop.Util;

namespace MyPasswordDesktop.Core
{
    /// <summary>
    /// HTTP service on 127.0.0.1:27432 for the Chrome extension. Built on
    /// <see cref="HttpListener"/> — the C# replacement for the Java
    /// <c>com.sun.net.httpserver</c> daemon.
    /// </summary>
    public sealed class HttpDaemon
    {
        public const int Port = 27432;

        /// <summary>The listener bound in <c>Program.Main</c> before the UI starts.</summary>
        public static HttpListener SharedListener;

        public static readonly object NotProcessed = new();

        private RequestController _controller;
        private HttpListener _listener;
        private bool _stopped;

        public void InitDispatcher()
        {
            _controller = new RequestController();
        }

        /// <summary>Begin the accept loop on a background task.</summary>
        public void Start()
        {
            _listener = SharedListener;
            Log.Info($"HTTP service listening on 127.0.0.1:{Port}");
            _ = Task.Run(AcceptLoopAsync);
        }

        /// <summary>Stop the daemon and close the vault. Idempotent.</summary>
        public void Stop()
        {
            if (_stopped)
            {
                return;
            }
            _stopped = true;
            try { _listener?.Stop(); } catch { /* ignore */ }
            VaultManager.Current?.Close();
        }

        private async Task AcceptLoopAsync()
        {
            while (_listener is { IsListening: true })
            {
                HttpListenerContext ctx;
                try
                {
                    ctx = await _listener.GetContextAsync();
                }
                catch (Exception)
                {
                    break; // listener stopped
                }
                _ = Task.Run(() => HandleContext(ctx));
            }
        }

        private void HandleContext(HttpListenerContext ctx)
        {
            HttpListenerRequest req = ctx.Request;
            string method = req.HttpMethod;
            string path = req.Url?.AbsolutePath ?? "/";
            string query = req.Url?.Query ?? "";
            if (query.StartsWith('?'))
            {
                query = query.Substring(1);
            }
            Log.Info($"http {method}: {path}");
            try
            {
                string body = null;
                if (method == "POST")
                {
                    using var reader = new StreamReader(req.InputStream, Encoding.UTF8);
                    body = reader.ReadToEnd();
                }
                if (method == "OPTIONS")
                {
                    SendCors(ctx);
                }
                else
                {
                    ProcessHttp(ctx, method, path, query, body);
                }
            }
            catch (Exception e)
            {
                Log.Error("http handle exception: " + e.Message, e);
                TrySetStatus(ctx, 400);
            }
            finally
            {
                try { ctx.Response.Close(); } catch { /* ignore */ }
            }
        }

        private static void SendCors(HttpListenerContext ctx)
        {
            HttpListenerResponse resp = ctx.Response;
            resp.AddHeader("Access-Control-Allow-Origin", "*");
            resp.AddHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            resp.AddHeader("Access-Control-Allow-Headers",
                "Content-Type, X-Extension-Id, X-Extension-Timestamp, X-Extension-Signature");
            resp.StatusCode = 204;
        }

        private void ProcessHttp(HttpListenerContext ctx, string method, string path, string query, string body)
        {
            object resp;
            bool validExtension = Extension.TrySetExtension(ctx.Request.Headers);
            try
            {
                if (!validExtension
                    && !path.StartsWith("/oauth/") && path != "/info" && path != "/pair"
                    && path != "/activate")
                {
                    throw new VaultException(ErrorCode.UNKNOWN_EXTENSION, "Unknown extension.");
                }
                resp = _controller.Handle(method, path, query, body);
            }
            catch (VaultException e)
            {
                Log.Warn($"http handle error {e.ErrorCode}: {e.Message}");
                var errorResp = new BaseResponse { error = e.ErrorCode, errorMessage = e.Message };
                SendResponse(ctx, "application/json", JsonUtils.ToJson(errorResp));
                return;
            }
            finally
            {
                if (validExtension)
                {
                    Extension.Remove();
                }
            }

            if (resp == null)
            {
                Log.Warn("http 200 but empty response.");
                ctx.Response.StatusCode = 200;
                return;
            }
            if (ReferenceEquals(resp, NotProcessed))
            {
                Log.Warn("http 404: " + path);
                ctx.Response.StatusCode = 404;
                return;
            }
            if (resp is string s)
            {
                if (s.StartsWith("redirect:"))
                {
                    ctx.Response.AddHeader("Location", s.Substring(9));
                    ctx.Response.StatusCode = 302;
                }
                else if (s.StartsWith("<html>"))
                {
                    SendResponse(ctx, "text/html", s);
                }
                else
                {
                    SendResponse(ctx, "application/json", s);
                }
                return;
            }
            SendResponse(ctx, "application/json", JsonUtils.ToJson(resp));
        }

        private static void SendResponse(HttpListenerContext ctx, string contentType, string content)
        {
            HttpListenerResponse resp = ctx.Response;
            resp.ContentType = contentType;
            resp.AddHeader("Access-Control-Allow-Origin", "*");
            byte[] bytes = Encoding.UTF8.GetBytes(content);
            resp.StatusCode = 200;
            resp.ContentLength64 = bytes.Length;
            resp.OutputStream.Write(bytes, 0, bytes.Length);
        }

        private static void TrySetStatus(HttpListenerContext ctx, int status)
        {
            try { ctx.Response.StatusCode = status; }
            catch { /* response may already be sent */ }
        }
    }
}
