//    jDownloader - Downloadmanager
//    Copyright (C) 2008  JD-Team jdownloader@freenet.de
//
//    This program is free software: you can redistribute it and/or modify
//    it under the terms of the GNU General Public License as published by
//    the Free Software Foundation, either version 3 of the License, or
//    (at your option) any later version.
//
//    This program  is distributed in the hope that it will be useful,
//    but WITHOUT ANY WARRANTY; without even the implied warranty of
//    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSSee the
//    GNU General Public License for more details.
//
//    You should have received a copy of the GNU General Public License
//    along with this program.  If not, see <http://wnu.org/licenses/>.

package jd.plugins.optional;

import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Vector;
import java.util.Map.Entry;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import jd.JDInit;
import jd.config.ConfigContainer;
import jd.config.ConfigEntry;
import jd.config.Configuration;
import jd.config.MenuItem;
import jd.config.Property;
import jd.event.ControlListener;
import jd.gui.skins.simple.JDAction;
import jd.gui.skins.simple.SimpleGUI;

import jd.plugins.DownloadLink;
import jd.plugins.PluginOptional;
import jd.plugins.Regexp;
import jd.utils.JDLocale;
import jd.utils.JDUtilities;


import org.mortbay.jetty.Request;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.handler.AbstractHandler;

public class JDRemoteControl extends PluginOptional implements ControlListener {

    private Server server;
    @SuppressWarnings("unused")
    private AbstractHandler serverHandler;

    @Override
    public String getCoder() {
        return "JD-Team";
    }

    @Override
    public String getPluginID() {
        return "0.0.0.2";
    }

    @Override
    public String getPluginName() {
        return JDLocale.L("plugins.optional.RemoteControl.name", "RemoteControl");
    }

    @Override
    public String getVersion() {
        return "0.0.0.2";
    }

    @Override
    public void enable(boolean enable) throws Exception {
        if (JDUtilities.getJavaVersion() >= 1.5) {
            if (enable) {
                logger.info("RemoteControl OK");
                initRemoteControl();
               JDUtilities.getController().addControlListener(this);
            }
        } else {
            logger.severe("Error initializing RemoteControl");
        }
    }

    public void initRemoteControl() {
        ConfigEntry cfg;
        config.addEntry(cfg = new ConfigEntry(ConfigContainer.TYPE_SPINNER, getProperties(), "PORT", JDLocale.L("plugins.optional.RemoteControl.port", "Port:"), 1000, 65500));
        cfg.setDefaultValue(10025);
    
        try {
            server = new Server(this.getProperties().getIntegerProperty("PORT", 10025));
            server.setHandler(new Serverhandler());
            server.start();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }



    @Override
    public String getRequirements() {
        return "JRE 1.5+";
    }



    public void actionPerformed(ActionEvent e) {
        if (server == null) return;
        try {
            if (this.server.isStarted() || this.server.isStarting()) {
                server.stop();
                JDUtilities.getGUI().showMessageDialog(this.getPluginName() + " stopped");
            } else {
                server = new Server(this.getProperties().getIntegerProperty("PORT", 10025));
                server.setHandler(new Serverhandler());
                server.start();
                JDUtilities.getGUI().showMessageDialog(this.getPluginName() + " started on port "+this.getProperties().getIntegerProperty("PORT", 10025));
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    @Override
    public ArrayList<MenuItem> createMenuitems() {
        ArrayList<MenuItem> menu = new ArrayList<MenuItem>();
        menu.add(new MenuItem("Toggle Start/Stop",0).setActionListener(this));
        return menu;
    }



    class Serverhandler extends AbstractHandler {

        public void handle(String target, HttpServletRequest request, HttpServletResponse response, int dispatch) throws IOException, ServletException {
            response.setContentType("text/html");
            response.setStatus(HttpServletResponse.SC_OK);
            SimpleGUI simplegui = SimpleGUI.CURRENTGUI;

            //---------------------------------------
            //Get
            //---------------------------------------
           
            //Get IP
            if (request.getRequestURI().equals("/get/ip")) {
                response.getWriter().println(JDUtilities.getIPAddress());
            }
            //Get Config
            if (request.getRequestURI().equals("/get/config")) {
                Property config = JDUtilities.getConfiguration();
                response.getWriter().println("<pre>");
                if (request.getParameterMap().containsKey("sub")) {
                
                    config = JDUtilities.getSubConfig(((String[]) request.getParameterMap().get("sub"))[0].toUpperCase());

                }
                Entry<String, Object> next;
                for (Iterator<Entry<String, Object>> it = config.getProperties().entrySet().iterator(); it.hasNext();) {
                    next = it.next();
                    response.getWriter().println(next.getKey() + " = " + next.getValue() + "\r\n");
                } response.getWriter().println("</pre>");
            }
            
            //Get Version
            if (request.getRequestURI().equals("/get/version")) {
                response.getWriter().println(JDUtilities.JD_VERSION + JDUtilities.getRevision());
            }
            
            //Get Current DLs
            if (request.getRequestURI().equals("/get/downloads/current")) {
                response.getWriter().println(JDUtilities.getController().getRunningDownloadNum());
            }
            
            //Get Current max. sim. DLs
            if (request.getRequestURI().equals("/get/downloads/max")) {
                response.getWriter().println(JDUtilities.getController().getDownloadLinks().size());
            }
            //Get finished DLs
            if (request.getRequestURI().equals("/get/downloads/finished")) {
                int counter = 0;
                Vector<DownloadLink> k = JDUtilities.getController().getDownloadLinks();

                for (int i = 0; i < k.size(); i++) {
                    if (k.get(i).getStatus() == DownloadLink.STATUS_DONE) counter++;
                }
                response.getWriter().println(counter);
            }
            
            //Get current Speed
            if (request.getRequestURI().equals("/get/speed")) {
                response.getWriter().println(JDUtilities.getController().getSpeedMeter() / 1000);
            }
            
            //Get IsReconnect
            if (request.getRequestURI().equals("/get/isreconnect")) {
                response.getWriter().println(!JDUtilities.getConfiguration().getBooleanProperty(Configuration.PARAM_DISABLE_RECONNECT, false));
            }
            //Get IsClipBoard
            if (request.getRequestURI().equals("/get/isclipboard")) {
                response.getWriter().println(JDUtilities.getController().getClipboard().isEnabled());
            }
            
            //---------------------------------------
            //Control
            //---------------------------------------
            
            //Do Start Download
            if (request.getRequestURI().equals("/action/start")) {
                JDUtilities.getController().startDownloads();
                response.getWriter().println("Downloads started");
            }
            
            //Do Pause Download
            if (request.getRequestURI().equals("/action/pause")) {
                simplegui.actionPerformed(new ActionEvent(this, JDAction.APP_PAUSE_DOWNLOADS, null));
                response.getWriter().println("Downloads paused");
            }
            
            //Do Stop Download
            if (request.getRequestURI().equals("/action/stop")) {
                JDUtilities.getController().stopDownloads();
                response.getWriter().println("Downloads stopped");
            }
            
            //Do Toggle Download
            if (request.getRequestURI().equals("/action/toggle")) {
                JDUtilities.getController().toggleStartStop();
                response.getWriter().println("Downloads toggled");
            }

            //Do Make Webupdate
            if (request.getRequestURI().equals("/action/update")) {
                new JDInit().doWebupdate(JDUtilities.getConfiguration().getIntegerProperty(Configuration.CID, -1), true);
                response.getWriter().println("Downloads stopped");
            }
            
            //Do Reconnect
            if (request.getRequestURI().equals("/action/reconnect")) {
                response.getWriter().println("Do Reconnect...");
                simplegui.doReconnect();
            }
            
            //Do Restart JD
            if (request.getRequestURI().equals("/action/restart")) {
                //TODO: Ausgabe der Meldung. z.Z. nur keine Verbindung
                response.getWriter().println("Restarting...");
                JDUtilities.restartJD();
            }
            
            //Do Shutdown JD
            if (request.getRequestURI().equals("/action/shutdown")) {
                //TODO: Ausgabe der Meldung. z.Z. nur keine Verbindung
                response.getWriter().println("Shutting down...");
                JDUtilities.getController().exit();
            }
            
            //Set Downloadlimit
            if (request.getRequestURI().matches("(?is).*/action/set/download/limit/[0-9]+.*")) {
                Integer newdllimit = Integer.parseInt(new Regexp(request.getRequestURI(),
                        "[\\s\\S]*/action/set/download/limit/([0-9]+).*")
                        .getFirstMatch());
                logger.fine("RemoteControl - Set max. Downloadspeed: " + newdllimit.toString() );
                JDUtilities.getSubConfig("DOWNLOAD").setProperty(Configuration.PARAM_DOWNLOAD_MAX_SPEED, newdllimit.toString());
                response.getWriter().println("newlimit=" + newdllimit);
            }
            
            //Set max. sim. Downloads
            if (request.getRequestURI().matches("(?is).*/action/set/download/max/[0-9]+.*")) {
                Integer newsimdl = Integer.parseInt(new Regexp(request.getRequestURI(),
                        "[\\s\\S]*/action/set/download/max/([0-9]+).*")
                        .getFirstMatch());
                logger.fine("RemoteControl - Set max. sim. Downloads: " + newsimdl.toString() );
                JDUtilities.getSubConfig("DOWNLOAD").setProperty(Configuration.PARAM_DOWNLOAD_MAX_SIMULTAN, newsimdl.toString());
                response.getWriter().println("newmax=" + newsimdl);
            }
            
            //OpenDialog Add-Links
            if (request.getRequestURI().equals("/action/open/add")) {
                simplegui.actionPerformed(new ActionEvent(this, JDAction.ITEMS_ADD, null));
                response.getWriter().println("Add-Links Dialog opened");
            }
            
            //OpenDialog Config
            if (request.getRequestURI().equals("/action/open/config")) {
                simplegui.actionPerformed(new ActionEvent(this, JDAction.APP_CONFIGURATION, null));
                response.getWriter().println("Config Dialog opened");
            }
            
            //OpenDialog Log
            if (request.getRequestURI().equals("/action/open/log")) {
                simplegui.actionPerformed(new ActionEvent(this, JDAction.APP_LOG, null));
                response.getWriter().println("Log Dialog opened");
            }
            
            //OpenDialog Container
            if (request.getRequestURI().equals("/action/open/containerdialog")) {
                simplegui.actionPerformed(new ActionEvent(this, JDAction.APP_LOAD_DLC, null));
                response.getWriter().println("Container Dialog opened");
            }
            
            //Open DLC Container
            if (request.getRequestURI().matches("(?is).*/action/add/container/[\\s\\S]+")) {
                String dlcfilestr = (new Regexp(request.getRequestURI(),
                "[\\s\\S]*/action/add/container/([\\s\\S]+)")
                .getFirstMatch());
                dlcfilestr = JDUtilities.htmlDecode(dlcfilestr);
                //wegen leerzeichen etc, die ja in urls verändert werden...
                JDUtilities.getController().loadContainerFile(new File(dlcfilestr));
                response.getWriter().println("Container opened. (" + dlcfilestr + ")");
            }
            
            //Set ClipBoard
            if (request.getRequestURI().matches("(?is).*/action/set/clipboard/.*")) {
                boolean newclip = Boolean.parseBoolean(new Regexp(request.getRequestURI(),
                     "[\\s\\S]*/action/set/clipboard/(.*)")
                     .getFirstMatch());
                logger.fine("RemoteControl - Set ClipBoard: " + newclip );
                if((JDUtilities.getController().getClipboard().isEnabled()) ^ (newclip)) /*C++ User:^ is equuvalent to XOR*/
                {
                    simplegui.actionPerformed(new ActionEvent(this, JDAction.APP_CLIPBOARD, null));
                    response.getWriter().println("clip=" + newclip + " (CHANGED=true)");
                }
                else
                {
                    response.getWriter().println("clip=" + newclip + " (CHANGED=false)");
                } 
            }
            
            //Set ReconnectEnabled
            if (request.getRequestURI().matches("(?is).*/action/set/reconnectenabled/.*")) {
                boolean newrc = Boolean.parseBoolean(new Regexp(request.getRequestURI(),
                     "[\\s\\S]*/action/set/reconnectenabled/(.*)")
                     .getFirstMatch());
                logger.fine("RemoteControl - Set ReConnect: " + newrc );
                if((!JDUtilities.getConfiguration().getBooleanProperty(Configuration.PARAM_DISABLE_RECONNECT, false)) ^ (newrc)) /*C++ User:^ is equuvalent to XOR*/
                {
                    simplegui.toggleReconnect(false);
                    response.getWriter().println("reconnect=" + newrc + " (CHANGED=true)");
                }
                else
                {
                    response.getWriter().println("reconnect=" + newrc + " (CHANGED=false)");
                } 
            }
            
  //        //Set use premium 
  //            if (request.getRequestURI().matches("(?is).*/action/download/premium/.*")) {
  //              boolean newuseprem = Boolean.parseBoolean(new Regexp(request.getRequestURI(),
  //                      "[\\s\\S]*/action/download/premium/(.*)")
  //                      .getFirstMatch());
  //              logger.fine("RemoteControl - Set Premium: " + newuseprem );
  //              JDUtilities.getConfiguration().setProperty(Configuration.PARAM_USE_GLOBAL_PREMIUM, newuseprem);
  //            response.getWriter().println("newprem=" + newuseprem);
  //        }
  //        

            
            ((Request) request).setHandled(true);
        }
    };

}
