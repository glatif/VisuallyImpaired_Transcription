// ESP32-S3 controller case for glasses.
// Layout: board and battery sit SIDE BY SIDE across the case's thickness (X),
// both occupying roughly the same stretch of length (Y) — wider than a
// front-to-back layout, but much shorter. The board is back-aligned, so its
// vertical/top-entry USB-C port sits at the rear of the case; the flexible
// ribbon cable bridges the gap forward to the camera pod.
// The USB-C connector stands upright off the board (vertical/top-entry, per
// the product photo) so it's cut through the TOP wall, near the case's rear.
// Units: mm. MEASURE YOUR PARTS and edit the values below (defaults are placeholders).
//
// Axes:  X = across (X=0 is the wall nearest the glasses arm / the clip;
//            +X points away from the head, toward the lid)
//        Y = along the arm (Y=0 is the front wall; the camera pod sticks out toward -Y)
//        Z = up
//
// Cross-section (looking along Y, i.e. front-on):
//   [arm wall] [ BOARD lane (board_t thick) ] [partition] [ BATTERY lane (batt_t thick) ] [lid]
// Both lanes run the same length in Y — whichever part is longer sets the case length.
//
// part = "base", "lid" or "both". Render with F6, export STL with F7.
// Print the base as modeled (flat bottom on the bed) and the lid as modeled (flat).

part = "both";

/* ---------- ESP32-S3 board + expansion board (measured by user) ---------- */
// Stacked assembly: 21 x 17.5 x 15 mm (L x W x H).
// 21mm runs along the arm (Y), 17.5mm is the up/down footprint (Z),
// 15mm is the full stack thickness across X (board + expansion + headers).
board_l = 21;     // board+expansion footprint length, along the arm (Y)
board_w = 17.5;   // board+expansion footprint width, up-down (Z)
board_t = 15;     // full stack thickness across X (board + expansion + headers)

// USB-C port: vertical/top-entry connector (per the photo). Most of these small
// boards have the connector right at the board's edge, so the opening is cut as
// an open notch through BOTH the top wall and the back wall — flush with the
// true rear of the case, not inset. MEASURE the port's footprint and adjust.
usb_w      = 9;   // opening width across X (the connector's width, as seen from above)
usb_len    = 6;   // how far the notch reaches forward from the back edge (along Y)
usb_margin = 0;   // gap between the board's true rear edge and the port, if any (usually 0)

/* ---------- Remote camera module (MEASURE) ---------- */
// The ribbon connector is a wide ZIF socket sandwiched BETWEEN the two stacked
// PCBs (not against either outer face), roughly centred on the board's height.
// The board sits toward the back of the case (see board_y0), so the flexible
// ribbon bridges the gap forward to reach the camera pod at the front.
cam_mod_w   = 9;   // camera module width (square-ish PCB)
cam_mod_len = 6;   // camera module thickness, lens tip to back of PCB
cam_hole    = 8;   // hole for the lens barrel
ribbon_w    = 15;  // ribbon/connector width — looks close to the board's 17.5mm width; verify
cam_dx      = 0;   // shift lens left/right (X) from the board lane's centre
pod_dz      = 0;   // shift the camera pod up(+)/down(-) — 0 assumes the connector is
                   // centred on the board's height, as in the photo

/* ---------- Battery (measured by user: 1.22 x 0.94 x 0.2 in) ---------- */
batt_l = 31.0;     // battery length (along Y)   -- 1.22 in
batt_w = 23.9;     // battery width (along Z)    -- 0.94 in
batt_t = 5.1;      // battery thickness (along X) -- 0.20 in

/* ---------- Slide switch, top wall over the battery lane (MEASURE) ---------- */
sw_w            = 8;  // opening length along Y
sw_h            = 4;  // opening width along X
sw_y_from_front = 12; // distance from the case's front wall to the switch centre

/* ---------- Glasses arm and clip (MEASURE at the spot behind the hinge) ---------- */
arm_t     = 5;     // arm thickness (left-right) where the clip sits
arm_h     = 10;    // arm height (up-down) where the clip sits
clip_wall = 2;     // clip rail thickness (thin = more flex)
lip       = 1.2;   // how far each hook overlaps the arm's inner face (bigger = grips harder)
clip_len  = 12;    // length of each of the two clip segments along the arm
clip_y0   = 4;     // distance from the case front wall to the first clip segment
arm_dz    = 0;     // raise the arm slot above the floor (keep 0 for easiest printing)

/* ---------- General ---------- */
wall     = 1.6;
clear    = 0.4;    // print tolerance around parts
lane_gap = 1.2;    // partition thickness between the board and battery lanes
lid_t    = 1.6;
lid_gap  = 0.25;
plug_d   = 1.2;
$fn      = 48;

/* ---------- Derived (don't edit) ---------- */
ix = board_t + lane_gap + batt_t + 2*clear;   // total interior thickness (X): both lanes + partition
iz = max(board_w, batt_w) + 2*clear;          // interior height (Z): sized to the taller part (battery)
iy = max(board_l, batt_l) + 2*clear;          // interior length (Y): sized to the longer part (battery)
ox = wall + ix;
oy = wall + iy + wall;
oz = iz + 2*wall;

x_b0   = wall + clear;                 // board lane inner (arm-facing) surface, X
x_bat0 = x_b0 + board_t + lane_gap;    // battery lane inner surface, X

board_y0 = oy - wall - board_l;        // board back-aligned, so the USB end sits at the rear of the
                                        // case. Leaves a gap of iy - board_l (~11mm) between the
                                        // board's ribbon connector and the camera pod — the ribbon is
                                        // flexible enough to bridge that gap on its own.
board_z0 = wall + (iz - board_w)/2;    // board centred vertically in the case

usb_x = x_b0 + board_t/2 - usb_w/2;
usb_y = oy - wall - usb_margin - usb_len;  // notch starts here and runs to the true back edge

sw_x = x_bat0 + batt_t/2;

pod_w  = max(cam_mod_w, ribbon_w) + 2*clear + 2*wall;  // pod height (Z), fits camera or ribbon
pod_d  = wall + cam_mod_len + 1;                        // pod depth (Y), sticks out at -Y
pod_zc = oz/2 + pod_dz;                                 // matches board's vertical centre
cam_x  = x_b0 + board_t/2 + cam_dx;                     // aligned with the board lane, not case centre

arm_x  = arm_t + 0.3;               // clip channel depth incl. slack
arm_z0 = clip_wall + arm_dz;        // bottom of the arm slot

module clip_segment(y0) {
    // bottom rail (sits on the print bed) and top rail, each with a hook lip
    // that catches the arm's inner face so it snaps in from the head side.
    translate([-(arm_x + clip_wall), y0, 0]) {
        cube([arm_x + clip_wall + 0.01, clip_len, clip_wall + arm_dz]);           // bottom rail
        translate([0, 0, arm_z0 + arm_h])
            cube([arm_x + clip_wall + 0.01, clip_len, clip_wall]);                // top rail
        cube([clip_wall, clip_len, arm_z0 + lip]);                                // bottom lip
        translate([0, 0, arm_z0 + arm_h - lip])
            cube([clip_wall, clip_len, lip + clip_wall]);                         // top lip
    }
}

module partition() {
    difference() {
        translate([x_b0 + board_t, wall, wall]) cube([lane_gap, iy, iz]);
        // wire pass-through near the front, for the battery leads to reach the board
        translate([x_b0 + board_t - 1, wall + 3, wall + iz/2 - 3]) cube([lane_gap + 2, 6, 6]);
    }
}

module board_posts() {
    post = 3;
    for (yy = [board_y0, board_y0 + board_l - post])
        for (zz = [board_z0, board_z0 + board_w - post])
            translate([wall - 0.01, yy, zz]) cube([clear + 0.01, post, post]);
}

module base() {
    difference() {
        union() {
            cube([ox, oy, oz]);                                                         // main box
            translate([0, -pod_d, pod_zc - pod_w/2]) cube([ox, pod_d + 0.01, pod_w]);    // camera pod
        }
        // main cavity, open on the outer (+X, lid) face
        translate([wall, wall, wall]) cube([ix + 1, iy, iz]);
        // camera pocket, joins the cavity so the board's ribbon has a free, short path
        translate([wall, -pod_d + wall, pod_zc - pod_w/2 + wall])
            cube([ix + 1, pod_d + 0.5, pod_w - 2*wall]);
        // lens hole through the pod front wall
        translate([cam_x, -pod_d - 1, pod_zc]) rotate([-90, 0, 0]) cylinder(d = cam_hole, h = wall + 2);
        // USB-C opening: an open notch through the top wall AND the back wall,
        // flush with the true rear of the case (vertical-entry connector at the board's edge)
        translate([usb_x, usb_y, oz - wall - 1]) cube([usb_w, usb_len + wall + 2, wall + 2]);
        // switch opening in the top wall, over the battery lane
        translate([sw_x - sw_h/2, wall + sw_y_from_front - sw_w/2, oz - wall - 1])
            cube([sw_h, sw_w, wall + 2]);
    }
    partition();
    board_posts();
    // two snap-on clip segments that grip the arm
    clip_segment(clip_y0);
    clip_segment(oy - clip_y0 - clip_len);
}

module lid() {
    // plate covers the outer face of the case and the camera pod
    translate([0, -pod_d, 0]) cube([lid_t, oy + pod_d, oz]);
    // short plug locates the lid in the cavity
    translate([-plug_d, wall + lid_gap, wall + lid_gap])
        cube([plug_d + 0.01, iy - 2*lid_gap, iz - 2*lid_gap]);
}

if (part == "base" || part == "both") base();
// lid laid flat (plate down, plug up) beside the base
if (part == "lid" || part == "both")
    translate([ox + 10, pod_d, lid_t]) rotate([0, 90, 0]) lid();
