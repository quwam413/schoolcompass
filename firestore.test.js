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

  // Regular user cannot delete another user's profile; platform admin CAN delete a violating user
  const attacker = testEnv.authenticatedContext(OTHER_PARENT_UID, {
    email: "carol@example.com",
    email_verified: true,
  }).firestore();
  await assertFails(attacker.collection("users").doc(PARENT_UID).delete());
  await assertSucceeds(superAdmin.collection("users").doc(PARENT_UID).delete());
});

test("Verified online schools save without registration gate, allow lightweight optional enrichment, in-place claim, and parent inquiries", async () => {
  const parent = testEnv.authenticatedContext(PARENT_UID).firestore();
  const claimingAdmin = testEnv.authenticatedContext(ADMIN_UID).firestore();
  const hijacker = testEnv.authenticatedContext(OTHER_PARENT_UID).firestore();
  const ts = pastDate(5000);

  const verifiedOnlinePayload = {
    adminUid: "",
    adminName: "",
    name: "Corona Secondary School, Agbara",
    description: "Verified online secondary boarding school.",
    address: "Agbara Estate, Ogun State",
    latitude: 6.5042,
    longitude: 3.1031,
    curriculum: "Nigerian & British",
    institutionType: "Primary & Secondary",
    faithBased: false,
    faithTradition: "Secular",
    specialSchool: false,
    specialNeedsSupport: "General Education",
    studentMix: "Mixed",
    gradeLevels: ["Junior Secondary", "Senior Secondary"],
    coursesOffered: ["WAEC", "IGCSE"],
    activities: [],
    sportsFocus: [],
    feeMin: 0,
    feeMax: 0,
    feeCurrency: "NGN",
    feePeriod: "Per Term",
    rankingScore: 86.0,
    districtRank: 15,
    rating: 4.6,
    tuitionTier: "$$",
    capacity: 600,
    enrolledCount: 0,
    admissionsOpen: true,
    websiteUrl: "https://coronaschools.org",
    verificationStatus: "VERIFIED_ONLINE",
    verificationSource: "Google Search & Maps Grounding",
    sourceCitations: [
      "[Google Search] Corona Schools Official | https://coronaschools.org",
      "[Google Maps] Corona Secondary School | https://www.google.com/maps/place/?q=place_id:ChIJcorona"
    ],
    googleMapsPlaceRef: "places/ChIJcorona",
    googleMapsUrl: "https://www.google.com/maps/place/?q=place_id:ChIJcorona",
    createdAt: ts,
    updatedAt: ts,
  };

  // Creating an unclaimed VERIFIED_ONLINE school succeeds
  await assertSucceeds(
    parent.collection("schools").doc("gmaps_ChIJcorona").set(verifiedOnlinePayload)
  );

  // Creating an unclaimed UNVERIFIED school without adminUid must fail
  await assertFails(
    parent.collection("schools").doc("bad_unclaimed").set({
      ...verifiedOnlinePayload,
      verificationStatus: "UNVERIFIED",
    })
  );

  // Any signed-in user can submit optional extra details (tuition bracket, admission window, boarding, photos)
  await assertSucceeds(
    parent.collection("schools").doc("gmaps_ChIJcorona").update({
      feeMin: 850000,
      feeMax: 1200000,
      tuitionTier: "$$$",
      admissionWindow: "Sept 2026 Intake Open",
      boardingOption: "Boarding",
      photoUrls: ["https://coronaschools.org/campus.jpg"],
      extraNotes: "Entrance exams hold in March and May.",
      lastEnrichedByName: "Alice Parent",
      updatedAt: pastDate(3000),
    })
  );

  // Community enrichment cannot change core identity fields (name, latitude, adminUid)
  await assertFails(
    parent.collection("schools").doc("gmaps_ChIJcorona").update({
      name: "Tampered School Name",
      updatedAt: pastDate(2500),
    })
  );

  // Parent can submit an immediate enrollment inquiry even before school registers
  await assertSucceeds(
    parent.collection("enrollments").doc("enr_online_1").set({
      schoolId: "gmaps_ChIJcorona",
      schoolName: "Corona Secondary School, Agbara",
      adminUid: "",
      parentUid: PARENT_UID,
      parentName: "Alice Parent",
      studentName: "Tomiwa",
      gradeApplying: "Junior Secondary",
      notes: "Requesting September 2026 admission pack.",
      status: "PENDING",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Optional claiming sets adminUid in place on existing verified-online doc
  await assertSucceeds(
    claimingAdmin.collection("schools").doc("gmaps_ChIJcorona").update({
      adminUid: ADMIN_UID,
      adminName: "Corona Admissions",
      updatedAt: pastDate(1500),
    })
  );

  // Once claimed, another user cannot hijack adminUid
  await assertFails(
    hijacker.collection("schools").doc("gmaps_ChIJcorona").update({
      adminUid: OTHER_PARENT_UID,
      adminName: "Hijacker",
      updatedAt: pastDate(500),
    })
  );
});

test("Platform admin delegation grants access strictly to owner and promoted admins while tracking app visits securely", async () => {
  const owner = testEnv.authenticatedContext("owner_uid", {
    email: "elebutequam413@gmail.com",
    email_verified: true,
  }).firestore();
  const carol = testEnv.authenticatedContext(OTHER_PARENT_UID, {
    email: "carol@example.com",
    email_verified: true,
  }).firestore();
  const alice = testEnv.authenticatedContext(PARENT_UID, {
    email: "alice@example.com",
    email_verified: true,
  }).firestore();
  const ts = pastDate(3000);

  // Regular users sign up (one parent, one school account)
  await assertSucceeds(
    alice.collection("users").doc(PARENT_UID).set({
      userId: PARENT_UID,
      role: "parent",
      displayName: "Alice Parent",
      email: "alice@example.com",
      homeLat: 6.4281,
      homeLng: 3.4219,
      searchRadiusKm: 10,
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    carol.collection("users").doc(OTHER_PARENT_UID).set({
      userId: OTHER_PARENT_UID,
      role: "admin",
      displayName: "Carol Academy",
      email: "carol@example.com",
      homeLat: 6.4474,
      homeLng: 3.4737,
      searchRadiusKm: 15,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Regular users can record one visit (+1) and a visit log for themselves, but cannot view totals or logs
  await assertSucceeds(
    alice.collection("app_analytics").doc("visits_summary").set({
      totalVisits: 1,
      androidVisits: 1,
      webVisits: 0,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    carol.collection("app_analytics").doc("visits_summary").update({
      totalVisits: 2,
      androidVisits: 1,
      webVisits: 1,
      updatedAt: pastDate(1500),
    })
  );
  await assertFails(
    alice.collection("app_analytics").doc("visits_summary").update({
      totalVisits: 99,
      androidVisits: 99,
      webVisits: 1,
      updatedAt: pastDate(1000),
    })
  );
  await assertSucceeds(
    alice.collection("visit_logs").doc("vis_alice_1").set({
      userId: PARENT_UID,
      userEmail: "alice@example.com",
      displayName: "Alice Parent",
      role: "parent",
      platform: "android",
      createdAt: ts,
    })
  );
  await assertFails(alice.collection("app_analytics").doc("visits_summary").get());
  await assertFails(alice.collection("visit_logs").get());

  // Regular user cannot self-promote or read other platform admins
  await assertFails(
    alice.collection("platform_admins").doc(PARENT_UID).set({
      userId: PARENT_UID,
      email: "alice@example.com",
      displayName: "Alice Parent",
      grantedByUid: PARENT_UID,
      grantedByEmail: "alice@example.com",
      createdAt: ts,
    })
  );
  await assertFails(carol.collection("platform_admins").get());
  await assertFails(carol.collection("users").get());

  // User can check their own platform_admins doc (returns non-existent before promotion)
  await assertSucceeds(carol.collection("platform_admins").doc(OTHER_PARENT_UID).get());

  // Primary owner promotes Carol to delegated platform admin
  await assertSucceeds(
    owner.collection("platform_admins").doc(OTHER_PARENT_UID).set({
      userId: OTHER_PARENT_UID,
      email: "carol@example.com",
      displayName: "Carol Academy",
      grantedByUid: "owner_uid",
      grantedByEmail: "elebutequam413@gmail.com",
      createdAt: ts,
    })
  );

  // Delegated admin Carol can now access all signed-up users, visits summary, and visit logs
  await assertSucceeds(carol.collection("users").get());
  await assertSucceeds(carol.collection("app_analytics").doc("visits_summary").get());
  await assertSucceeds(carol.collection("visit_logs").get());
  await assertSucceeds(carol.collection("platform_admins").get());

  // Delegated admin still cannot promote/demote other admins (owner-only privilege)
  await assertFails(
    carol.collection("platform_admins").doc(PARENT_UID).set({
      userId: PARENT_UID,
      email: "alice@example.com",
      displayName: "Alice Parent",
      grantedByUid: OTHER_PARENT_UID,
      grantedByEmail: "carol@example.com",
      createdAt: ts,
    })
  );

  // Alice remains blocked
  await assertFails(alice.collection("users").get());
  await assertFails(alice.collection("app_analytics").doc("visits_summary").get());

  // Owner revokes Carol's admin access -> Carol immediately loses admin privileges
  await assertSucceeds(owner.collection("platform_admins").doc(OTHER_PARENT_UID).delete());
  await assertFails(carol.collection("users").get());
  await assertFails(carol.collection("app_analytics").doc("visits_summary").get());
});

test("Users, schools, adverts, and tutors can set, update, and clear valid bannerImageUrl while rejecting unsafe URLs", async () => {
  const user = testEnv.authenticatedContext(PARENT_UID).firestore();
  const owner = testEnv.authenticatedContext(ADMIN_UID).firestore();
  const ts = pastDate(5000);
  const sampleDataUri = "data:image/jpeg;base64,/9j/4AAQSkZJRgABAQAAAQABAAD/";

  // User profile banner create, data-URI update, and reject unsafe scheme
  await assertSucceeds(
    user.collection("users").doc(PARENT_UID).set({
      userId: PARENT_UID,
      role: "parent",
      displayName: "Alice Parent",
      email: "alice@example.com",
      homeLat: 6.4281,
      homeLng: 3.4219,
      searchRadiusKm: 10,
      bannerImageUrl: "https://cdn.example.com/alice-banner.jpg",
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    user.collection("users").doc(PARENT_UID).update({
      bannerImageUrl: sampleDataUri,
      updatedAt: pastDate(2000),
    })
  );
  await assertFails(
    user.collection("users").doc(PARENT_UID).update({
      bannerImageUrl: "javascript:alert(1)",
      updatedAt: pastDate(1000),
    })
  );

  // School owner banner create, update, and invalid scheme rejection
  await assertSucceeds(
    owner.collection("schools").doc("sch_banner_test").set({
      ...makeSchoolPayload(ADMIN_UID),
      bannerImageUrl: "https://cdn.example.com/school-banner.jpg",
    })
  );
  await assertSucceeds(
    owner.collection("schools").doc("sch_banner_test").update({
      bannerImageUrl: sampleDataUri,
      updatedAt: pastDate(1500),
    })
  );
  await assertFails(
    owner.collection("schools").doc("sch_banner_test").update({
      bannerImageUrl: "ftp://bad.example.com/img.png",
      updatedAt: pastDate(1000),
    })
  );

  // Advert banner create & update
  await assertSucceeds(
    owner.collection("adverts").doc("adv_banner_test").set({
      ownerUid: ADMIN_UID,
      advertiserName: "Meadowhall Dual Academy",
      title: "Admissions Showcase",
      body: "Tour our campus and scholarships.",
      category: "School Admissions",
      curriculumFocus: "Nigerian & British",
      planTier: "Featured Spotlight",
      planPriceLabel: "₦35,000 / 14 days",
      durationDays: 14,
      targetLocation: "Lekki Phase 1, Lagos",
      latitude: 6.4474,
      longitude: 3.4737,
      ctaLabel: "Inquire Now",
      bannerImageUrl: "https://cdn.example.com/ad-banner.jpg",
      status: "ACTIVE",
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    owner.collection("adverts").doc("adv_banner_test").update({
      bannerImageUrl: sampleDataUri,
      updatedAt: pastDate(1000),
    })
  );

  // Tutor banner create & update
  await assertSucceeds(
    owner.collection("tutors").doc("tut_banner_test").set({
      teacherUid: ADMIN_UID,
      fullName: "Mr. Samuel Kalu",
      sex: "Male",
      headline: "Cambridge Physics Mentor",
      bio: "Interactive virtual physics coaching.",
      curriculumTracks: ["Cambridge"],
      cambridgeSubjects: ["Cambridge Physics"],
      nigerianSubjects: [],
      allSubjects: ["Cambridge Physics"],
      qualifications: "B.Sc Physics, TRCN",
      yearsExperience: 8,
      hourlyRate: 8000,
      currency: "NGN",
      meetingPlatform: "Zoom",
      availability: "Weekdays 4 PM",
      locationLabel: "Ikoyi, Lagos",
      latitude: 6.4549,
      longitude: 3.4346,
      rating: 4.9,
      onlineActive: true,
      bannerImageUrl: sampleDataUri,
      createdAt: ts,
      updatedAt: ts,
    })
  );
});

test("Page visits track unique visitors per school or tutor, block self-visits, and restrict reads to owner and visitor", async () => {
  const visitor = testEnv.authenticatedContext(PARENT_UID).firestore();
  const secondVisitor = testEnv.authenticatedContext(OTHER_PARENT_UID).firestore();
  const schoolOwner = testEnv.authenticatedContext(ADMIN_UID).firestore();
  const outsider = testEnv.authenticatedContext(OTHER_ADMIN_UID).firestore();
  const ts = pastDate(4000);
  const visitDocId = "pv_school_oak_alice_101";

  // Visitor checks non-existent visit doc before creating (allowed by resource == null rule)
  await assertSucceeds(visitor.collection("page_visits").doc(visitDocId).get());

  // First visit creates record with visitHits == 1
  await assertSucceeds(
    visitor.collection("page_visits").doc(visitDocId).set({
      pageType: "school",
      targetId: "school_oak",
      targetTitle: "Meadowhall Dual Academy",
      ownerUid: ADMIN_UID,
      visitorUid: PARENT_UID,
      visitorName: "Alice Parent",
      visitHits: 1,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Repeat visit increments visitHits by +1
  await assertSucceeds(
    visitor.collection("page_visits").doc(visitDocId).update({
      targetTitle: "Meadowhall Dual Academy",
      visitorName: "Alice Parent",
      visitHits: 2,
      updatedAt: pastDate(1000),
    })
  );

  // Forged visitHits jump (> +1) must fail
  await assertFails(
    visitor.collection("page_visits").doc(visitDocId).update({
      visitHits: 10,
      updatedAt: pastDate(500),
    })
  );

  // Self-visit (ownerUid == visitorUid) must fail
  await assertFails(
    schoolOwner.collection("page_visits").doc("pv_self_visit").set({
      pageType: "tutor",
      targetId: "tut_1",
      targetTitle: "Principal Bob",
      ownerUid: ADMIN_UID,
      visitorUid: ADMIN_UID,
      visitorName: "Principal Bob",
      visitHits: 1,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Second unique visitor for tutor page succeeds
  await assertSucceeds(
    secondVisitor.collection("page_visits").doc("pv_tutor_carol_202").set({
      pageType: "tutor",
      targetId: "tut_1",
      targetTitle: "Principal Bob",
      ownerUid: ADMIN_UID,
      visitorUid: OTHER_PARENT_UID,
      visitorName: "Carol Parent",
      visitHits: 1,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Owner can list all page_visits scoped by ownerUid (mirrors SchoolRepository.observeOwnerPageVisits)
  const ownerSnap = await assertSucceeds(
    schoolOwner.collection("page_visits").where("ownerUid", "==", ADMIN_UID).get()
  );
  if (ownerSnap.size !== 2) {
    throw new Error(`Expected 2 unique page visits for owner, got ${ownerSnap.size}`);
  }

  // Third party cannot read or list someone else's page_visits
  await assertFails(outsider.collection("page_visits").doc(visitDocId).get());
  await assertFails(outsider.collection("page_visits").get());
});

