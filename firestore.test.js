const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const PARENT_UID = "parent_alice_101";
const OTHER_PARENT_UID = "parent_carol_202";
const ADMIN_UID = "admin_bob_303";
const OTHER_ADMIN_UID = "admin_dave_404";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

function pastDate(msOffset = 2000) {
  return new Date(Date.now() - msOffset);
}

function makeSchoolPayload(adminUid = ADMIN_UID) {
  const now = pastDate(5000);
  return {
    adminUid,
    adminName: "Principal Bob",
    name: "Meadowhall Dual Academy",
    description: "Dual Nigerian & British curriculum with robotics and varsity football.",
    address: "14 Admiralty Way, Lekki Phase 1",
    latitude: 6.4474,
    longitude: 3.4737,
    curriculum: "Nigerian & British",
    faithBased: false,
    faithTradition: "Secular",
    specialSchool: false,
    specialNeedsSupport: "Inclusive Learning Center",
    studentMix: "Mixed",
    gradeLevels: ["Primary", "Middle", "High"],
    activities: ["Robotics", "Debate", "Orchestra"],
    sportsFocus: ["Football (Soccer)", "Swimming", "Athletics"],
    feeMin: 450000,
    feeMax: 950000,
    feeCurrency: "NGN",
    feePeriod: "Per Term",
    rankingScore: 95.5,
    districtRank: 2,
    rating: 4.8,
    tuitionTier: "$$",
    capacity: 600,
    enrolledCount: 480,
    admissionsOpen: true,
    contactPhone: "+234-800-555-0192",
    createdAt: now,
    updatedAt: now,
  };
}

test("Unauthenticated users cannot read or write any collection", async () => {
  const unauth = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauth.collection("schools").get());
  await assertFails(unauth.collection("users").doc(PARENT_UID).get());
  await assertFails(unauth.collection("announcements").get());
  await assertFails(unauth.collection("schools").doc("school_1").set(makeSchoolPayload()));
});

test("User profiles enforce owner isolation and reject PII cross-reads and shadow fields", async () => {
  const alice = testEnv.authenticatedContext(PARENT_UID).firestore();
  const carol = testEnv.authenticatedContext(OTHER_PARENT_UID).firestore();
  const ts = pastDate(5000);

  await assertSucceeds(
    alice.collection("users").doc(PARENT_UID).set({
      userId: PARENT_UID,
      role: "parent",
      displayName: "Alice Parent",
      email: "alice@example.com",
      phone: "555-0101",
      homeAddress: "Victoria Island",
      homeLat: 6.4281,
      homeLng: 3.4219,
      searchRadiusKm: 10,
      childSummary: "Grade 5",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    alice.collection("users").doc(PARENT_UID).update({
      searchRadiusKm: 25,
      favoriteSchoolIds: ["school_oak", "ext_node_123456"],
      updatedAt: pastDate(1000),
    })
  );

  await assertFails(carol.collection("users").doc(PARENT_UID).get());
  await assertFails(carol.collection("users").get());
  await assertFails(carol.collection("users").doc(PARENT_UID).update({ favoriteSchoolIds: ["hijacked"], updatedAt: pastDate(1000) }));

  await assertFails(
    alice.collection("users").doc(PARENT_UID).update({
      isVerified: true,
      updatedAt: pastDate(1000),
    })
  );
});

test("Schools allow signed-in reads and admin-owner writes with valid fee range, curriculum, and sports", async () => {
  const admin = testEnv.authenticatedContext(ADMIN_UID).firestore();
  const otherAdmin = testEnv.authenticatedContext(OTHER_ADMIN_UID).firestore();
  const parent = testEnv.authenticatedContext(PARENT_UID).firestore();

  await assertSucceeds(admin.collection("schools").doc("school_oak").set(makeSchoolPayload(ADMIN_UID)));
  await assertSucceeds(parent.collection("schools").get());

  // Invalid fee range (feeMax < feeMin) must fail
  const invalidFeeSchool = makeSchoolPayload(ADMIN_UID);
  invalidFeeSchool.feeMin = 900000;
  invalidFeeSchool.feeMax = 400000;
  await assertFails(admin.collection("schools").doc("school_bad_fee").set(invalidFeeSchool));

  // Owner admin can update profile, fees, curriculum, and sports in real time
  await assertSucceeds(
    admin.collection("schools").doc("school_oak").update({
      enrolledCount: 485,
      feeMin: 500000,
      feeMax: 1050000,
      sportsFocus: ["Football (Soccer)", "Basketball", "Swimming"],
      admissionsOpen: true,
      updatedAt: pastDate(1000),
    })
  );

  await assertFails(
    otherAdmin.collection("schools").doc("school_oak").update({
      name: "Hijacked School",
      updatedAt: pastDate(1000),
    })
  );
  await assertFails(
    parent.collection("schools").doc("school_oak").set(makeSchoolPayload(ADMIN_UID))
  );

  // Admin can claim an existing internet-geolocated school and update verification status
  const claimedSchool = {
    ...makeSchoolPayload(ADMIN_UID),
    externalPlaceId: "node_49182736",
    websiteUrl: "https://meadowhall.example.edu",
    verificationStatus: "VERIFIED",
    verificationSource: "OpenStreetMap Directory + Website HTTP 200",
  };
  await assertSucceeds(admin.collection("schools").doc("ext_node_49182736").set(claimedSchool));

  // Invalid verificationStatus must be rejected
  await assertFails(
    admin.collection("schools").doc("ext_node_49182736").update({
      verificationStatus: "SELF_ATTESTED",
      updatedAt: pastDate(500),
    })
  );

  // Tertiary institutions (University, Polytechnic, College of Education) with coursesOffered succeed
  await assertSucceeds(
    admin.collection("schools").doc("uni_coastal_1").set({
      ...makeSchoolPayload(ADMIN_UID),
      name: "Coastal Meridian University",
      institutionType: "University",
      gradeLevels: ["Undergraduate", "Postgraduate"],
      coursesOffered: ["Computer Science", "Medicine & Surgery", "Law"],
    })
  );
  await assertFails(
    admin.collection("schools").doc("uni_bad_type").set({
      ...makeSchoolPayload(ADMIN_UID),
      institutionType: "Bootcamp",
    })
  );
});

test("Enrollments enforce parent create, school admin status updates, and terminal state lock", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("schools").doc("school_oak").set(makeSchoolPayload(ADMIN_UID));
  });

  const parent = testEnv.authenticatedContext(PARENT_UID).firestore();
  const otherParent = testEnv.authenticatedContext(OTHER_PARENT_UID).firestore();
  const admin = testEnv.authenticatedContext(ADMIN_UID).firestore();
  const ts = pastDate(5000);

  await assertFails(
    parent.collection("enrollments").doc("enr_bad").set({
      schoolId: "school_oak",
      schoolName: "Meadowhall Dual Academy",
      adminUid: ADMIN_UID,
      parentUid: PARENT_UID,
      parentName: "Alice Parent",
      studentName: "Leo Parent",
      gradeApplying: "Grade 5",
      status: "APPROVED",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    parent.collection("enrollments").doc("enr_1").set({
      schoolId: "school_oak",
      schoolName: "Meadowhall Dual Academy",
      adminUid: ADMIN_UID,
      parentUid: PARENT_UID,
      parentName: "Alice Parent",
      studentName: "Leo Parent",
      gradeApplying: "Grade 5",
      notes: "Interested in robotics and football",
      status: "PENDING",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertFails(
    parent.collection("enrollments").doc("enr_1").update({
      status: "APPROVED",
      updatedAt: pastDate(1000),
    })
  );

  await assertSucceeds(parent.collection("enrollments").where("parentUid", "==", PARENT_UID).get());
  await assertSucceeds(admin.collection("enrollments").where("adminUid", "==", ADMIN_UID).get());
  await assertFails(otherParent.collection("enrollments").get());
  await assertFails(otherParent.collection("enrollments").doc("enr_1").get());

  await assertSucceeds(
    admin.collection("enrollments").doc("enr_1").update({
      status: "DECLINED",
      adminResponse: "Grade 5 full this term.",
      updatedAt: pastDate(1000),
    })
  );
  await assertFails(
    admin.collection("enrollments").doc("enr_1").update({
      status: "APPROVED",
      updatedAt: pastDate(500),
    })
  );
});

test("Students, attendance, and grades enforce parent read-only and admin write", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("schools").doc("school_oak").set(makeSchoolPayload(ADMIN_UID));
  });

  const admin = testEnv.authenticatedContext(ADMIN_UID).firestore();
  const parent = testEnv.authenticatedContext(PARENT_UID).firestore();
  const otherParent = testEnv.authenticatedContext(OTHER_PARENT_UID).firestore();
  const ts = pastDate(5000);

  await assertFails(
    parent.collection("students").doc("stu_1").set({
      parentUid: PARENT_UID,
      adminUid: ADMIN_UID,
      schoolId: "school_oak",
      schoolName: "Meadowhall Dual Academy",
      studentName: "Leo Parent",
      gradeLevel: "Grade 5",
      attendanceRate: 100.0,
      presentDays: 10,
      absentDays: 0,
      tardyDays: 0,
      gpa: 4.0,
      overallAverage: 99.0,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    admin.collection("students").doc("stu_1").set({
      parentUid: PARENT_UID,
      adminUid: ADMIN_UID,
      schoolId: "school_oak",
      schoolName: "Meadowhall Dual Academy",
      studentName: "Leo Parent",
      gradeLevel: "Grade 5",
      attendanceRate: 96.0,
      presentDays: 24,
      absentDays: 1,
      tardyDays: 0,
      gpa: 3.8,
      overallAverage: 92.5,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    admin.collection("students").doc("stu_1").collection("attendance").doc("att_1").set({
      parentUid: PARENT_UID,
      adminUid: ADMIN_UID,
      dateLabel: "Nov 21, 2026",
      status: "PRESENT",
      note: "On time",
      createdAt: ts,
    })
  );

  await assertSucceeds(
    admin.collection("students").doc("stu_1").collection("grades").doc("grd_1").set({
      parentUid: PARENT_UID,
      adminUid: ADMIN_UID,
      subject: "Mathematics",
      score: 94.0,
      letterGrade: "A",
      term: "Fall 2026",
      teacherRemark: "Strong problem solving",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(parent.collection("students").where("parentUid", "==", PARENT_UID).get());
  await assertSucceeds(
    parent.collection("students").doc("stu_1").collection("attendance").where("parentUid", "==", PARENT_UID).get()
  );
  await assertSucceeds(
    parent.collection("students").doc("stu_1").collection("grades").where("parentUid", "==", PARENT_UID).get()
  );

  await assertFails(
    parent.collection("students").doc("stu_1").update({ gpa: 4.0, updatedAt: pastDate(500) })
  );
  await assertFails(otherParent.collection("students").doc("stu_1").get());
});

test("Conversations and messages enforce memberUids membership", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("schools").doc("school_oak").set(makeSchoolPayload(ADMIN_UID));
  });

  const parent = testEnv.authenticatedContext(PARENT_UID).firestore();
  const admin = testEnv.authenticatedContext(ADMIN_UID).firestore();
  const outsider = testEnv.authenticatedContext(OTHER_PARENT_UID).firestore();
  const ts = pastDate(5000);
  const convId = `school_oak_${PARENT_UID}`;

  await assertSucceeds(
    parent.collection("conversations").doc(convId).set({
      schoolId: "school_oak",
      schoolName: "Meadowhall Dual Academy",
      parentUid: PARENT_UID,
      parentName: "Alice Parent",
      adminUid: ADMIN_UID,
      adminName: "Principal Bob",
      memberUids: [PARENT_UID, ADMIN_UID],
      lastMessage: "Hello, is admissions open for Grade 5?",
      lastSenderUid: PARENT_UID,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    parent.collection("conversations").doc(convId).collection("messages").doc("msg_1").set({
      senderUid: PARENT_UID,
      senderName: "Alice Parent",
      senderRole: "parent",
      text: "Hello, is admissions open for Grade 5?",
      memberUids: [PARENT_UID, ADMIN_UID],
      createdAt: ts,
    })
  );

  await assertSucceeds(admin.collection("conversations").where("memberUids", "array-contains", ADMIN_UID).get());
  await assertSucceeds(
    admin.collection("conversations").doc(convId).collection("messages").where("memberUids", "array-contains", ADMIN_UID).get()
  );

  await assertFails(outsider.collection("conversations").doc(convId).get());
  await assertFails(
    outsider.collection("conversations").doc(convId).collection("messages").doc("msg_2").set({
      senderUid: OTHER_PARENT_UID,
      senderName: "Carol",
      senderRole: "parent",
      text: "Spying",
      memberUids: [PARENT_UID, ADMIN_UID],
      createdAt: ts,
    })
  );
});

test("Announcements allow school admin create and signed-in list", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("schools").doc("school_oak").set(makeSchoolPayload(ADMIN_UID));
  });

  const admin = testEnv.authenticatedContext(ADMIN_UID).firestore();
  const parent = testEnv.authenticatedContext(PARENT_UID).firestore();
  const ts = pastDate(3000);

  await assertSucceeds(
    admin.collection("announcements").doc("ann_1").set({
      schoolId: "school_oak",
      schoolName: "Meadowhall Dual Academy",
      adminUid: ADMIN_UID,
      title: "Open House Saturday",
      body: "Tour our robotics lab and varsity pitch at 10 AM.",
      category: "Event",
      eventDate: "Nov 28, 2026 • 10:00 AM",
      createdAt: ts,
    })
  );

  await assertSucceeds(parent.collection("announcements").get());

  await assertFails(
    parent.collection("announcements").doc("ann_2").set({
      schoolId: "school_oak",
      schoolName: "Meadowhall Dual Academy",
      adminUid: PARENT_UID,
      title: "Fake Alert",
      body: "School closed",
      category: "Urgent",
      createdAt: ts,
    })
  );
});

test("Adverts allow owner create/update/pause and reject cross-user tampering", async () => {
  const owner = testEnv.authenticatedContext(ADMIN_UID).firestore();
  const other = testEnv.authenticatedContext(PARENT_UID).firestore();
  const ts = pastDate(4000);

  await assertSucceeds(
    owner.collection("adverts").doc("adv_1").set({
      ownerUid: ADMIN_UID,
      advertiserName: "Meadowhall Dual Academy",
      title: "2026 Scholarship Entrance Exam",
      body: "Apply for merit scholarships across Nigerian & British tracks.",
      category: "School Admissions",
      curriculumFocus: "Nigerian & British",
      planTier: "Featured Spotlight",
      planPriceLabel: "₦35,000 / 14 days",
      durationDays: 14,
      targetLocation: "Lekki Phase 1, Lagos",
      latitude: 6.4474,
      longitude: 3.4737,
      ctaLabel: "Apply Now",
      contactInfo: "admissions@meadowhall.edu.ng",
      status: "ACTIVE",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(other.collection("adverts").get());
  await assertSucceeds(
    owner.collection("adverts").doc("adv_1").update({
      status: "PAUSED",
      updatedAt: pastDate(1000),
    })
  );
  await assertFails(
    other.collection("adverts").doc("adv_1").update({
      title: "Hijacked Ad",
      updatedAt: pastDate(500),
    })
  );
});

test("Progressive Tutors and online tutoring sessions enforce teacher/client ownership", async () => {
  const teacher = testEnv.authenticatedContext(ADMIN_UID).firestore();
  const client = testEnv.authenticatedContext(PARENT_UID).firestore();
  const outsider = testEnv.authenticatedContext(OTHER_PARENT_UID).firestore();
  const ts = pastDate(4000);

  await assertSucceeds(
    teacher.collection("tutors").doc("tut_1").set({
      teacherUid: ADMIN_UID,
      fullName: "Mrs. Funke Adeyemi",
      sex: "Female",
      headline: "Cambridge IGCSE & WAEC Mathematics Specialist",
      bio: "12 years teaching British IGCSE & Nigerian WAEC Further Mathematics online.",
      curriculumTracks: ["Cambridge", "Nigerian"],
      cambridgeSubjects: ["Cambridge IGCSE Mathematics", "Cambridge Physics"],
      nigerianSubjects: ["WAEC / NECO Mathematics", "Further Mathematics"],
      allSubjects: ["Cambridge IGCSE Mathematics", "Cambridge Physics", "WAEC / NECO Mathematics", "Further Mathematics"],
      qualifications: "M.Sc. Mathematics, TRCN & Cambridge Certified",
      yearsExperience: 12,
      hourlyRate: 8500,
      currency: "NGN",
      meetingPlatform: "Google Meet",
      availability: "Mon–Sat • 4:00 PM – 8:00 PM",
      locationLabel: "Lekki Phase 1, Lagos",
      latitude: 6.4474,
      longitude: 3.4737,
      rating: 4.9,
      onlineActive: true,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(client.collection("tutors").get());
  await assertFails(
    client.collection("tutors").doc("tut_1").update({
      hourlyRate: 100,
      updatedAt: pastDate(1000),
    })
  );

  await assertSucceeds(
    client.collection("tutor_sessions").doc("ses_1").set({
      tutorId: "tut_1",
      tutorName: "Mrs. Funke Adeyemi",
      teacherUid: ADMIN_UID,
      clientUid: PARENT_UID,
      clientName: "Alice Parent",
      studentName: "Leo Parent",
      curriculumTrack: "Cambridge & Nigerian",
      subject: "Cambridge IGCSE Mathematics",
      preferredSchedule: "Tuesdays & Thursdays • 5:00 PM",
      notes: "Needs past paper practice",
      status: "REQUESTED",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(client.collection("tutor_sessions").where("clientUid", "==", PARENT_UID).get());
  await assertSucceeds(teacher.collection("tutor_sessions").where("teacherUid", "==", ADMIN_UID).get());
  await assertFails(outsider.collection("tutor_sessions").doc("ses_1").get());

  await assertSucceeds(
    teacher.collection("tutor_sessions").doc("ses_1").update({
      status: "CONFIRMED",
      meetingLink: "https://meet.google.com/abc-defg-hij",
      updatedAt: pastDate(1000),
    })
  );

  await assertFails(
    outsider.collection("tutor_sessions").doc("ses_1").update({
      status: "CANCELLED",
      updatedAt: pastDate(500),
    })
  );
});

test("Advert inquiries enforce sender create, owner status updates, and cross-user isolation", async () => {
  const owner = testEnv.authenticatedContext(ADMIN_UID).firestore();
  const parent = testEnv.authenticatedContext(PARENT_UID).firestore();
  const outsider = testEnv.authenticatedContext(OTHER_PARENT_UID).firestore();
  const ts = pastDate(4000);

  await owner.collection("adverts").doc("adv_inq_1").set({
    ownerUid: ADMIN_UID,
    advertiserName: "Meadowhall Dual Academy",
    title: "Admissions Open 2026",
    body: "Enroll now for primary & secondary tracks.",
    category: "School Admissions",
    curriculumFocus: "Nigerian & British",
    planTier: "Featured Spotlight",
    planPriceLabel: "₦35,000 / 14 days",
    durationDays: 14,
    targetLocation: "Lekki Phase 1, Lagos",
    latitude: 6.4474,
    longitude: 3.4737,
    ctaLabel: "Inquire Now",
    status: "ACTIVE",
    createdAt: ts,
    updatedAt: ts,
  });

  await assertSucceeds(
    parent.collection("advert_inquiries").doc("inq_1").set({
      advertId: "adv_inq_1",
      advertTitle: "Admissions Open 2026",
      ownerUid: ADMIN_UID,
      senderUid: PARENT_UID,
      senderName: "Alice Parent",
      senderEmail: "alice@example.com",
      phone: "+234-800-111-2222",
      message: "Please share entrance exam dates for JSS1.",
      status: "NEW",
      createdAt: ts,
    })
  );

  await assertSucceeds(parent.collection("advert_inquiries").where("senderUid", "==", PARENT_UID).get());
  await assertSucceeds(owner.collection("advert_inquiries").where("ownerUid", "==", ADMIN_UID).get());
  await assertFails(outsider.collection("advert_inquiries").doc("inq_1").get());
  await assertFails(
    outsider.collection("advert_inquiries").doc("inq_1").update({ status: "CLOSED" })
  );

  await assertSucceeds(
    owner.collection("advert_inquiries").doc("inq_1").update({ status: "CONTACTED" })
  );
});

test("Payments enforce payer create, recipient/admin confirmation, and reject tampering", async () => {
  const payer = testEnv.authenticatedContext(PARENT_UID).firestore();
  const recipient = testEnv.authenticatedContext(ADMIN_UID).firestore();
  const outsider = testEnv.authenticatedContext(OTHER_PARENT_UID).firestore();
  const ts = pastDate(4000);

  await assertSucceeds(
    payer.collection("payments").doc("pay_1").set({
      payerUid: PARENT_UID,
      payerName: "Alice Parent",
      payerEmail: "alice@example.com",
      recipientUid: ADMIN_UID,
      recipientName: "Meadowhall Dual Academy",
      paymentType: "SCHOOL_APPLICATION_FEE",
      referenceId: "enr_1",
      referenceTitle: "Application Fee • Meadowhall Dual Academy",
      amount: 15000,
      currency: "NGN",
      paymentMethod: "Bank Transfer",
      gatewayProvider: "Direct Bank",
      gatewayReference: "SC-BNK-91827364",
      receiptNumber: "RCP-2026-88412",
      status: "PENDING_VERIFICATION",
      notes: "GTBank transfer ref 88412",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Zero or negative amounts must fail
  await assertFails(
    payer.collection("payments").doc("pay_zero").set({
      payerUid: PARENT_UID,
      payerName: "Alice Parent",
      payerEmail: "alice@example.com",
      recipientUid: ADMIN_UID,
      recipientName: "Meadowhall Dual Academy",
      paymentType: "SCHOOL_APPLICATION_FEE",
      referenceId: "enr_1",
      referenceTitle: "Free Fee",
      amount: 0,
      currency: "NGN",
      paymentMethod: "Card Payment",
      gatewayProvider: "Paystack",
      gatewayReference: "SC-PSK-123456",
      receiptNumber: "RCP-2026-00001",
      status: "PAID",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(payer.collection("payments").where("payerUid", "==", PARENT_UID).get());
  await assertSucceeds(recipient.collection("payments").where("recipientUid", "==", ADMIN_UID).get());
  await assertFails(outsider.collection("payments").doc("pay_1").get());

  // Payer cannot self-confirm a pending bank transfer, nor tamper with amount
  await assertFails(
    payer.collection("payments").doc("pay_1").update({
      status: "PAID",
      updatedAt: pastDate(1000),
    })
  );
  await assertFails(
    recipient.collection("payments").doc("pay_1").update({
      amount: 999999,
      status: "PAID",
      updatedAt: pastDate(1000),
    })
  );

  // Recipient can verify & mark PAID
  await assertSucceeds(
    recipient.collection("payments").doc("pay_1").update({
      status: "PAID",
      notes: "Confirmed in school bank statement",
      updatedAt: pastDate(1000),
    })
  );
});

test("Bootstrapped super-admin can list users, enrollments, and payments for platform oversight while regular users cannot", async () => {
  const regular = testEnv.authenticatedContext(PARENT_UID, {
    email: "alice@example.com",
    email_verified: true,
  }).firestore();
  const superAdmin = testEnv.authenticatedContext("super_admin_uid", {
    email: "elebutequam413@gmail.com",
    email_verified: true,
  }).firestore();
  const ts = pastDate(3000);

  await regular.collection("users").doc(PARENT_UID).set({
    userId: PARENT_UID,
    role: "parent",
    displayName: "Alice Parent",
    email: "alice@example.com",
    homeLat: 6.4281,
    homeLng: 3.4219,
    searchRadiusKm: 10,
    createdAt: ts,
    updatedAt: ts,
  });

  await assertFails(regular.collection("users").get());
  await assertFails(regular.collection("enrollments").get());
  await assertFails(regular.collection("payments").get());

  await assertSucceeds(superAdmin.collection("users").get());
  await assertSucceeds(superAdmin.collection("enrollments").get());
  await assertSucceeds(superAdmin.collection("payments").get());
  await assertSucceeds(superAdmin.collection("advert_inquiries").get());
  await assertSucceeds(superAdmin.collection("tutor_sessions").get());
});


