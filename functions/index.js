const functions = require("firebase-functions");
const admin = require("firebase-admin");

admin.initializeApp();

// Triggers when a new photo is added to Firestore
exports.onNewPhoto = functions.firestore
  .document("photos/{photoId}")
  .onCreate(async (snap, context) => {
    const data = snap.data();
    const receiver = data.receiver;
    const sender = data.sender;

    if (!receiver) return null;

    // Get receiver's FCM token from Firestore
    const tokenDoc = await admin
      .firestore()
      .collection("tokens")
      .doc(receiver)
      .get();

    if (!tokenDoc.exists) return null;

    const token = tokenDoc.data().fcmToken;
    if (!token) return null;

    // Send push notification via DATA payload only
    const message = {
      token: token,
      // Notice: The 'notification' block is completely gone!
      data: {
        type: "new_photo",
        sender: sender,
        title: `💌 New moment from ${sender}!`,
        body: "Tap to see your surprise 🌸",
      },
      android: {
        priority: "high", // Keep this! It ensures the OS wakes up your app quickly.
      },
    };

    try {
      await admin.messaging().send(message);
      console.log("Notification sent to", receiver);
    } catch (error) {
      console.error("Error sending notification:", error);
    }

    return null;
  });