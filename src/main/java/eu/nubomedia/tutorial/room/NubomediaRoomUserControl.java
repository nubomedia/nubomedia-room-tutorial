/*
 * (C) Copyright 2016 NUBOMEDIA (http://www.nubomedia.eu)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package eu.nubomedia.tutorial.room;

import java.io.IOException;

import org.kurento.client.FaceOverlayFilter;
import org.kurento.client.MediaElement;
import org.kurento.jsonrpc.Transaction;
import org.kurento.jsonrpc.message.Request;
import org.kurento.module.msdata.KmsMsData;
import org.kurento.room.NotificationRoomManager;
import org.kurento.room.api.pojo.ParticipantRequest;
import org.kurento.room.rpc.JsonRpcUserControl;
import org.kurento.room.rpc.ParticipantSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonObject;

/**
 * User control that extends the publishing video capabilities:
 * <ul>
 * <li>applies a face overlay filter</li>
 * <li>enables data channels and connects a demo KMS module to the publisher's pipeline</li>
 * </ul>
 *
 * @author Boni Garcia (boni.garcia@urjc.es)
 * @since 6.4.1
 */
public class NubomediaRoomUserControl extends JsonRpcUserControl {

  private static final String SESSION_ATTRIBUTE_HAT_FILTER = "hatFilter";

  private static final String CUSTOM_REQUEST_HAT_PARAM = "hat";

  private static final Logger log = LoggerFactory.getLogger(NubomediaRoomUserControl.class);

  private String hatUrl = "http://files.kurento.org/img/mario-wings.png";
  private float offsetXPercent = -0.35F;
  private float offsetYPercent = -1.2F;
  private float widthPercent = 1.6F;
  private float heightPercent = 1.6F;

  public NubomediaRoomUserControl(NotificationRoomManager roomManager) {
    super(roomManager);
  }

  @Override
  public void publishVideo(Transaction transaction, Request<JsonObject> request,
      ParticipantRequest participantRequest) {
    String pid = participantRequest.getParticipantId();
    ParticipantSession session = getParticipantSession(transaction);
    if (session == null) {
      log.warn("No participant session info found when publishing for session {}", pid);
    } else {
      if (session.useDataChannels()) {
        // add kms module/filter
        log.info("Applying KMS Data Channel module to session {}", pid);
        try {
          // Media logic
          KmsMsData kmsMsData = new KmsMsData.Builder(roomManager.getPipeline(pid)).build();
          roomManager.addMediaElement(pid, kmsMsData);
        } catch (Exception e) {
          log.warn("Unable to add msdatademopaasgraph filter to session {}", pid, e);
        }
      }
    }
    super.publishVideo(transaction, request, participantRequest);
  }

  @Override
  public void customRequest(Transaction transaction, Request<JsonObject> request,
      ParticipantRequest participantRequest) {
    try {
      if (request.getParams() == null || request.getParams().get(CUSTOM_REQUEST_HAT_PARAM) == null) {
        throw new RuntimeException("Request element '" + CUSTOM_REQUEST_HAT_PARAM + "' is missing");
      }
      boolean hatOn = request.getParams().get(CUSTOM_REQUEST_HAT_PARAM).getAsBoolean();
      String pid = participantRequest.getParticipantId();
      if (hatOn) {
        if (transaction.getSession().getAttributes().containsKey(SESSION_ATTRIBUTE_HAT_FILTER)) {
          throw new RuntimeException("Hat filter already on");
        }
        log.info("Applying face overlay filter to session {}", pid);
        FaceOverlayFilter faceOverlayFilter = new FaceOverlayFilter.Builder(
            roomManager.getPipeline(pid)).build();
        faceOverlayFilter.setOverlayedImage(this.hatUrl, this.offsetXPercent, this.offsetYPercent,
            this.widthPercent, this.heightPercent);
        roomManager.addMediaElement(pid, faceOverlayFilter);
        transaction.getSession().getAttributes()
            .put(SESSION_ATTRIBUTE_HAT_FILTER, faceOverlayFilter);
      } else {
        if (!transaction.getSession().getAttributes().containsKey(SESSION_ATTRIBUTE_HAT_FILTER)) {
          throw new RuntimeException("This user has no hat filter yet");
        }
        log.info("Removing face overlay filter from session {}", pid);
        roomManager.removeMediaElement(pid, (MediaElement) transaction.getSession().getAttributes()
            .get(SESSION_ATTRIBUTE_HAT_FILTER));
        transaction.getSession().getAttributes().remove(SESSION_ATTRIBUTE_HAT_FILTER);
      }
      transaction.sendResponse(new JsonObject());
    } catch (Exception e) {
      log.error("Unable to handle custom request", e);
      try {
        transaction.sendError(e);
      } catch (IOException e1) {
        log.warn("Unable to send error response", e1);
      }
    }
  }
}
