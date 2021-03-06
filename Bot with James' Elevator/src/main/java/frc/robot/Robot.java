package frc.robot;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.channels.CancelledKeyException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Properties;
import edu.wpi.first.wpilibj.DigitalInput;

import edu.wpi.first.wpilibj.Compressor;
import edu.wpi.first.wpilibj.TimedRobot;
import frc.robot.subsystems.*;
import frc.robot.subsystems.SwerveDrivetrain.WheelType;
import frc.robot.utilities.*;
import frc.robot.RRLogger;

import edu.wpi.first.wpilibj.smartdashboard.SendableChooser;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;

/**
 * The VM is configured to automatically run this class, and to call the functions corresponding to each mode, as described in the IterativeRobot
 * documentation. If you change the name of this class or the package after creating this project, you must also update the manifest file in the
 * resource directory.
 */
public class Robot extends TimedRobot {

	private SendableChooser<Integer> autoChooser;

	private Compressor compressor;

	public static XboxController xbox1 = new XboxController(0);
	public static XboxController xbox2 = new XboxController(1);

//	private JetsonServer jet;
//	private Thread t;
	private SwerveDrivetrain driveTrain;
	private IMU imu;

//	private boolean robotBackwards;

	private double robotOffset;

	private int disabled = 0;

	private double[][] commands;
	private int arrayIndex = -1;
	private int autoMove = 0;
	private int translateType;
	private double autonomousAngle;
	private double tSpeed;
	private double rSpeed;
	private double previousDistance = 0.0;
	private double currentDistance = 0.0;
	private boolean override;
	private boolean driveDone;
	private boolean turnDone;
	private boolean timeDone;
	private double offsetDeg;
	private double prevOffset = 0;
	private double timeBase;
	private boolean timeCheck;
	private double smoothArc;
	private double smoothAccelerate;
	private double smoothAccelerateNum;
	private double initialAngle;
	private final double minSpeed = 0.2;

	private int dx = -1;

	private double FWD;
	private double STR;
	private double RCW;

	private double[] prevFWD = {0.0, 0.0, 0.0, 0.0, 0.0};
	private double[] prevSTR = {0.0, 0.0, 0.0, 0.0, 0.0};
	private double[] prevRCW = {0.0, 0.0, 0.0, 0.0, 0.0};

	private  double[] hatchSetpoints = {7.07, 33.26, 60/*62.8*/};
	private  double[] ballSetpoints = {12.4, 39.5, 60/*63.6*/};


	private double wheelRamp = 0;
	private double rampRate = 0;
	private double currentRampTime = 0;
	private double prevRampTime = 0;

	private double keepAngle;

	private boolean autonomous;

	private boolean fieldOriented = true; // start on field orientation
	private boolean previousOrientedButton = false;
	private boolean currentOrientedButton = false;

	private PIDController SwerveCompensate;

	private double lead;

	private double robotRotation;

	private double imuOffset = 0;

	private Properties application = new Properties();
	private File offsets = new File("/home/lvuser/deploy/SWERVE_OFFSET.txt");

	private boolean rumble = false;
	private int rumbleTime = 0;

	private Acceleration accel;
	private Acceleration rcwAccel;

	private Acceleration decelFWD;
	private Acceleration decelSTR;
	private Acceleration decelRCW;
	private int cmdCounter = 0;

	private double[] ArcXs = new double[3];
	private double[] ArcYs = new double[3];
	double[] curveVelocity = {0.0, 0.0};
	private static int position = 0;



	private void keepAngle() {
		//This causes the point rotation when wheels are flipped over y=x
		// LABEL keepAngle
		SwerveCompensate.enable();

		double leadNum = SmartDashboard.getNumber("leadNum", 0);
		lead = RCW * leadNum;

//		System.out.println(Math.abs(xbox1.LStickX()));

		// This will update the angle to keep the robot's orientation
		if (Math.abs(xbox1.RStickX()) > 0.05 || // If right stick is pressed
				(Math.abs(FWD) < 0.05 && Math.abs(STR) < 0.05) && // If left stick is not pressed
						(xbox1.DPad() == -1) && // If dpad is not pressed
						(!autonomous)) { // If teleop

			SwerveCompensate.setPID(0.015/*0.015*/, 0.0, 0.0);
			keepAngle = imu.getAngle();
		} else {
			SwerveCompensate.setPID(0.008/*0.008*/, 0.0, 0.0);

			SwerveCompensate.setInput(imu.getAngle());
			SwerveCompensate.setSetpoint(keepAngle);

			if (!SwerveCompensate.onTarget()) {
				SwerveCompensate.setPID(0.007/*0.005*/, SmartDashboard.getNumber("CompensateI", 0.0), SmartDashboard.getNumber("CompensateD", 0.0));
			}

			robotRotation = SwerveCompensate.performPID();

			RCW = robotRotation;

			SmartDashboard.putNumber("Robot Rotation 1", robotRotation);
		}
	}

	/**
	 * Each potentiometer is positioned slightly differently so its initial value is different than the others,
	 * so even when the wheels are pointing straight there are differences.
	 * Proper values may be found and must be calculated for each wheel.
	 */
	private void loadOffsets() {
		// LABEL load offsets

		// Set the position of each wheel from a file on the roborio
		SwerveDrivetrain.swerveModules.get(WheelType.FRONT_RIGHT).setPosition(Vector.load(application.getProperty("front_right_pos", "0.0,0.0")));
		SwerveDrivetrain.swerveModules.get(WheelType.FRONT_LEFT).setPosition(Vector.load(application.getProperty("front_left_pos", "0.0,0.0")));
		SwerveDrivetrain.swerveModules.get(WheelType.BACK_LEFT).setPosition(Vector.load(application.getProperty("back_left_pos", "0.0,0.0")));
		SwerveDrivetrain.swerveModules.get(WheelType.BACK_RIGHT).setPosition(Vector.load(application.getProperty("back_right_pos", "0.0,0.0")));

//		SwerveDrivetrain.swerveModules.get(WheelType.FRONT_RIGHT).setDrift(application.getProperty("front_right_drift", "0.0,0.0").split(","));
//		SwerveDrivetrain.swerveModules.get(WheelType.FRONT_LEFT).setDrift(application.getProperty("front_left_drift", "0.0,0.0").split(","));
//		SwerveDrivetrain.swerveModules.get(WheelType.BACK_LEFT).setDrift(application.getProperty("back_left_drift", "0.0,0.0").split(","));
//		SwerveDrivetrain.swerveModules.get(WheelType.BACK_RIGHT).setDrift(application.getProperty("back_right_drift", "0.0,0.0").split(","));

//		robotBackwards = Boolean.parseBoolean(application.getProperty("robot_backwards", "false"));
}

	private void autonomousAngle(double angle) {
		// LABEL autonomous angle

		SwerveCompensate.setInput(imu.getAngle());
		SwerveCompensate.setSetpoint(angle);

		SwerveCompensate.setTolerance(7);
		if (!SwerveCompensate.onTarget()) {
			SwerveCompensate.setPID(0.013, SmartDashboard.getNumber("CompensateI", 0.0), SmartDashboard.getNumber("CompensateD", 0.0));
		} else {
			SwerveCompensate.setPID(0.015, SmartDashboard.getNumber("CompensateI", 0.0), SmartDashboard.getNumber("CompensateD", 0.0));
		}

		robotRotation = SwerveCompensate.performPID();

		RCW = (robotRotation);
	}

	//Might be helpful for verification
	//Once robotDistance+= calculateLength distance, finish step
	public double calculateLength(double[] ArcXs, double[] ArcYs) {
        double vx = 2 * (ArcXs[1] - ArcXs[0]);
        double vy = 2 * (ArcYs[1] - ArcYs[0]);
        double wx = ArcXs[2] - 2 * ArcXs[1] + ArcXs[0];
        double wy = ArcYs[2] - 2 * ArcYs[1] + ArcYs[0];

        double uu = 4 * (Math.pow(wx, 2) + Math.pow(wy, 2));
        if (uu < 0.00001) {
//            System.out.println(Math.sqrt(Math.pow(ArcXs[2] - ArcXs[0], 2) + Math.pow(ArcYs[2] - ArcYs[0], 2)));
        }

        double vv = 4 * (vx * wx + vy * wy);
        double ww = Math.pow(vx, 2) + Math.pow(vy, 2);

        double t1 = (float) (2 * Math.sqrt(uu * (uu + vv + ww)));
        double t2 = 2 * uu + vv;
        double t3 = vv * vv - 4 * uu * ww;
        double t4 = (float) (2 * Math.sqrt(uu * ww));

        return (((t1 * t2 - t3 * Math.log(t2 + t1) - (vv * t4 - t3 * Math.log(vv + t4))) / (8 * Math.pow(uu, 1.5))));
    }

	//This function will return the scaled FWD and STR commands based off the arc points
    public double[] calculatePath(double[] ArcXs, double[] ArcYs, double distance) {
		//We need to scale returned FWD and STR cmds based off
		double[] velocity = new double[2];
		double[] move = new double[4];
		distance /= calculateLength(ArcXs, ArcYs);
		double nextDistance = distance + 0.00001; //scale to speed later
		double xCoordVector1 = 2*ArcXs[0] - 4*ArcXs[1] + 2*ArcXs[2];
		double xCoordVector2 = -2*ArcXs[0] + 2*ArcXs[1];

		double yCoordVector1 = 2*ArcXs[0] - 4*ArcXs[1] + 2*ArcXs[2];
		double yCoordVector2 = -2*ArcXs[0] + 2*ArcXs[1];

		//tX = move[0] ???
		//tY = move[1] ???
//		double newTX = tX + nextDistance/((distance*(tX*xCoordVector1 +xCoordVector2)));
//		double newTY = tX + nextDistance/((distance*(tY*xCoordVector1 +xCoordVector2)));
		// distance += 0.00005;
        //if distance == 1, we're done

		//move[0] = robot's xcoord, move[1] = ycoord
		//move[2] = robot's xcoord in next tiny second, move[3] = that but for ycoord
        move[0] = (ArcXs[0] - 2 * ArcXs[1] + ArcXs[2]) * Math.pow(distance, 2) + 2 * (ArcXs[1] - ArcXs[0]) * distance + ArcXs[0];
        move[1] = (ArcYs[0] - 2 * ArcYs[1] + ArcYs[2]) * Math.pow(distance, 2) + 2 * (ArcYs[1] - ArcYs[0]) * distance + ArcYs[0];
		
		move[2] = (ArcXs[0] - 2 * ArcXs[1] + ArcXs[2]) * Math.pow(nextDistance, 2) + 2 * (ArcXs[1] - ArcXs[0]) * nextDistance + ArcXs[0];
        move[3] = (ArcYs[0] - 2 * ArcYs[1] + ArcYs[2]) * Math.pow(nextDistance, 2) + 2 * (ArcYs[1] - ArcYs[0]) * nextDistance + ArcYs[0];

        // distanceDelta += Math.sqrt(Math.pow(prevPoint[0] - move[0], 2) + Math.pow(prevPoint[1] - move[1], 2));
		//When distanceDelta = calculateDistance, we should be done
        // prevPoint[0] = move[0];
		// prevPoint[1] = move[1];

		velocity[0] = move[3]-move[1]; //FWD
		velocity[1] = move[2]-move[0]; //STR

		//t = t + nextDistance/(totalArcLength*v1*v2)
		if (Math.abs(velocity[0]) > Math.abs(velocity[1])) {
			velocity[1] = Math.signum(velocity[0])*velocity[1]/velocity[0];
			velocity[0] = Math.signum(velocity[0]);
		} else {
			velocity[0] = Math.signum(velocity[1])*velocity[0]/velocity[1];
			velocity[1] = Math.signum(velocity[1]);
		}

//		System.out.println(velocity[0] + " || " + velocity[1]);


		return velocity;
	}
	
	/**
	 * This function is run when the robot is first started up and should be used for any initialization code.
	 */
	public void robotInit() {
		// LABEL robot init
		// Load the wheel offset file from the roborio

		try {
			FileInputStream in = new FileInputStream(offsets);
			application.load(in);
		} catch (IOException e) {
			e.printStackTrace();
		}

		// Connect to jetson
//		try {
//			jet = new JetsonServer((short) 5800, (short) 5801);
//			t = new Thread(jet);
//			t.start();
//			jet.setDisabled();
//		} catch (IOException e) {
//			throw new RuntimeException(e);
//		}

		compressor = new Compressor(0);

		xbox1.setDeadband(0.01);

		Time.start();

		SwerveDrivetrain.loadPorts();
		driveTrain = new SwerveDrivetrain();
		loadOffsets();

//		SmartDashboard.putNumber("CompensateP", 0.02);
//		SmartDashboard.putNumber("CompensateI", 0.0);
//		SmartDashboard.putNumber("CompensateD", 0.0);

		SmartDashboard.putNumber("Autonomous Delay", 0);

		autoChooser = new SendableChooser<>();
		autoChooser.addDefault("Middle", 1);
		autoChooser.addObject("Left", 2);
		autoChooser.addObject("Right", 3);
		autoChooser.addObject("Forward", 4);
		SmartDashboard.putData("Autonomous Mode Chooser", autoChooser);

		imu = new IMU();
		imu.IMUInit();

		keepAngle = imu.getAngle();

		SwerveCompensate = new PIDController(0.015, 0.00, 0.00);
		SwerveCompensate.setContinuous(true);
		SwerveCompensate.setOutputRange(-1.0, 1.0);
		SwerveCompensate.setInputRange(0.0, 360.0);
		SwerveCompensate.setTolerance(1.0);

		SwerveCompensate.enable();

		accel = new Acceleration();
		decelFWD = new Acceleration();
		decelSTR = new Acceleration();

		decelRCW = new Acceleration();
		rcwAccel = new Acceleration();
	}

	public void autonomousInit() {
		// LABEL autonomous init
//		jet.setAuto(); // this line is important because it does clock synchronization

		timeCheck = true;
		imu.reset(0);

		String choice;

		choice = "/home/lvuser/deploy/RightStartToRightRocket.csv";

		SmartDashboard.putString("Autonomous File", choice);

		imu.reset(0);
		arrayIndex = 0;
		initialAngle = imu.getAngle();
		turnDone = false;
		driveDone = false;

		// Fill the array of commands from a csv file on the roborio
		try {
			List<String> lines = Files.readAllLines(Paths.get(choice));
			commands = new double[lines.size()][];
			int l = 0;
			for (String line : lines) {
				String[] parts = line.split(",");
				double[] linel = new double[parts.length];
				int i = 0;
				for (String part : parts) {
					linel[i++] = Double.parseDouble(part);
//					System.out.println(Double.parseDouble(part));
				}
				commands[l++] = linel;
			}
		} catch (IOException e) {
			e.printStackTrace();
		} catch (NumberFormatException e) {
			System.err.println("Error in configuration!");
		}
	}

	/**
	 * This function is called periodically during autonomous
	 */
	public void autonomousPeriodic() {
		// LABEL autonomous periodic

		SmartDashboard.putNumber("IMU Angle", imu.getAngle());

		if (timeCheck) {
			timeBase = Time.get();
			timeCheck = false;
		}

//		SmartDashboard.putNumber("Elapsed Time", Time.get());

		switch (autoMove) {

			// Pause the robot for x seconds at the start of auto
			case 0:

				driveTrain.drive(new Vector(FWD, STR), 0);
				if (Time.get() > timeBase + SmartDashboard.getNumber("Autonomous Delay", 0)) {
					autoMove = 1;
				}
				previousDistance = currentDistance;

				break;


			case 1:

				SmartDashboard.putNumber("Array Index", arrayIndex);
//				SmartDashboard.putNumber("IMU Angle", imu.getAngle());

				/*
				 * 0 = translate speed, 1 = rotate speed, 2 = direction to translate, 3 = direction to face,
				 * 4 = distance(in), 5 = How to accelerate(0 = no modification, 1 = transition, 2 = accelerate, 3 = decelerate)
				 * 6 = ArcXs[0], 7 = ArcXs[1], 8 = ArcXs[2],  9 = ArcYs[0], 10 = ArcYs[1], 11 = ArcYs[2]
				 * 12 = time out(seconds), 13 = imu offset
				 * 14 = hatch placement (0 = none, 1 = hatch low, 2 = hatch mid, 3 = hatch high),
				  * 15 = ball placement (0 = none, 1 = ball low, 2 = ball mid, 3 = ball high),
				 * 16 = intake/outake (0 = none, 1 = get hatch, 2 = put hatch, 3 = get ball, 4 = put ball)
				 *
				 */

				//Only use translation for RCW and Arc Speed

				tSpeed = commands[arrayIndex][0];
				rSpeed = commands[arrayIndex][1];

				ArcXs[0] = commands[arrayIndex][6];
				ArcXs[1] = commands[arrayIndex][7];
				ArcXs[2] = commands[arrayIndex][8];

				ArcYs[0] = commands[arrayIndex][9];
				ArcYs[1] = commands[arrayIndex][10];
				ArcYs[2] = commands[arrayIndex][11];

				if (commands[arrayIndex][14] != 0.0) {
					int setpoint = Math.round(Math.round(commands[arrayIndex][14]-1));
					//Elevator.setPosition(hatchSetpoints[setpoint]);
				}

				if (commands[arrayIndex][15] != 0.0) {
					int setpoint = Math.round(Math.round(commands[arrayIndex][14]-1));
					//Elevator.setPosition(hatchSetpoints[setpoint]);
				}

				if (commands[arrayIndex][16] == 0.0) {
					BallIntake.set(false, false);
					HatchIntake.set(false, false); //Obsolete for the hatch?
				} else if (commands[arrayIndex][16] == 1.0) {
					HatchIntake.set(true, false); //Maybe have limit switch to know when to stop instead of doing it at next command
				} else if (commands[arrayIndex][16] == 2.0) {
					HatchIntake.set(false, true);
				} else if (commands[arrayIndex][16] == 3.0) {
					BallIntake.set(true, false);
				} else if (commands[arrayIndex][16] == 4.0) {
					BallIntake.set(false, true);
				}

				if (commands[arrayIndex][2] != -1) {
					FWD = Math.cos(Math.toRadians(commands[arrayIndex][2]));
					STR = Math.sin(Math.toRadians(commands[arrayIndex][2]));
				} else {
					FWD = 0;
					STR = 0;
				}

				if (ArcXs[0] != 999) {
					curveVelocity = calculatePath(ArcXs, ArcYs, SmartDashboard.getNumber("Distance", 0) - previousDistance);
					STR = -curveVelocity[0];
					FWD = curveVelocity[1];
					commands[arrayIndex][4] = calculateLength(ArcXs, ArcYs);
				}
				
				keepAngle = commands[arrayIndex][3];

				if (Math.abs(MathUtils.getAngleError(imu.getAngle(), commands[arrayIndex][3])) < 5.0) {
					initialAngle = imu.getAngle();
					turnDone = true;
				} else {
					double direction;
					direction = MathUtils.getAngleError(initialAngle, commands[arrayIndex][3]);
					if (Math.abs(direction) > 180.0) {
						direction *= -1.0;
					}
					RCW = Math.signum(direction);
					turnDone = false;
				}

				if (commands[arrayIndex][5] == 1) {
					smoothAccelerateNum = (MathUtils.convertRange(previousDistance, previousDistance + commands[arrayIndex][4], commands[arrayIndex][0], commands[arrayIndex+1][0], SmartDashboard.getNumber("Distance", 0)));
					smoothAccelerate = smoothAccelerateNum;
					FWD *= smoothAccelerate;
					STR *= smoothAccelerate;
				} else if (commands[arrayIndex][5] == 2) {
					smoothAccelerateNum = (MathUtils.convertRange(previousDistance, previousDistance + commands[arrayIndex][4], minSpeed, commands[arrayIndex][0], SmartDashboard.getNumber("Distance", 0)));
					smoothAccelerate = smoothAccelerateNum;
					FWD *= smoothAccelerate;
					STR *= smoothAccelerate;
				} else if (commands[arrayIndex][5] == 3) {
					smoothAccelerateNum = (MathUtils.convertRange(previousDistance, previousDistance + commands[arrayIndex][4], commands[arrayIndex][0], minSpeed, SmartDashboard.getNumber("Distance", 0)));
					 smoothAccelerate = smoothAccelerateNum;
					FWD *= smoothAccelerate;
					STR *= smoothAccelerate;
				} else {
					FWD *= tSpeed;
					STR *= tSpeed;
				}

				Vector driveCommands;
				driveCommands = MathUtils.convertOrientation(Math.toRadians(imu.getAngle()), FWD, STR);
				FWD = driveCommands.getY();
				STR = driveCommands.getX();
				RCW *= rSpeed;

				SmartDashboard.putNumber("Previous Distance", previousDistance);

				if ((Math.abs(SmartDashboard.getNumber("Distance", 0) - previousDistance) >= commands[arrayIndex][4])) {
					driveDone = true;
//					STR = 0;
//					FWD = 0;
				}

				SmartDashboard.putNumber("Auto Distance Gone", Math.abs(currentDistance - previousDistance));
				SmartDashboard.putNumber("Auto Distance Command", commands[arrayIndex][4]);

				SwerveCompensate.setTolerance(1);

				if (Time.get() > timeBase + commands[arrayIndex][12] && commands[arrayIndex][12] > 0) {
					override = true;
				} else if (commands[arrayIndex][12] == 0) {
					timeDone = true;
				}

				imuOffset = commands[arrayIndex][13];

				if (turnDone) {
					keepAngle();
				}

				SmartDashboard.putNumber("FWD", FWD);
				SmartDashboard.putNumber("STR", STR);
				SmartDashboard.putNumber("RCW", RCW);

//				if (robotBackwards) {
//					driveTrain.drive(new Vector(-STR, FWD), RCW);
//				} else {
					driveTrain.drive(new Vector(STR, FWD), -RCW);
					//FIXME, if point rotation happens, switch FL with BR and L with R
//				}

				if (override) {
					driveDone = true;
					turnDone = true;
				}

//				System.out.println("Drive: " + driveDone);
//				System.out.println("Turn: " + turnDone);
//				System.out.println("Coll: " + collisionDone);
//				System.out.println("Time: " + timeDone);
//				System.out.println("TimeNum: " + Time.get() + " | " + (timeBase + commands[arrayIndex][10]));

				SmartDashboard.putNumber("Array", arrayIndex);

				if (driveDone) {
					arrayIndex++;
					driveDone = false;
					initialAngle = imu.getAngle();
					previousDistance = SmartDashboard.getNumber("Distance", 0);//currentDistance;
					turnDone = false;
					timeDone = false;
					override = false;
					timeBase = Time.get();

				}
				break;
		}
	}

	public void teleopInit() {
//		jet.startTeleop();

		imu.setOffset(imuOffset);
//Sets ids
		SwerveDrivetrain.swerveModules.get(WheelType.FRONT_RIGHT).setID(1);
		SwerveDrivetrain.swerveModules.get(WheelType.FRONT_LEFT).setID(2);
		SwerveDrivetrain.swerveModules.get(WheelType.BACK_RIGHT).setID(4);
		SwerveDrivetrain.swerveModules.get(WheelType.BACK_LEFT).setID(3);

//		RRLogger.start();

	}

	/**
	 * This function is called periodically during operator control
	 */
	public void teleopPeriodic() {
		// LABEL teleop periodic
		autonomous = false;

		SmartDashboard.putNumber("IMU Angle", imu.getAngle());
		SmartDashboard.putNumber("Elevator Setpoint", position);
		SmartDashboard.putNumber("L Trig", xbox2.LTrig());
		SmartDashboard.putNumber("R Trig", xbox2.RTrig());


		xbox1.setDeadband(0.2);
		xbox2.setDeadband(0.2);

		if (xbox2.RB() == true) { //Hatch
			if (xbox2.DPad() == 180) { //Low
				position = 0; //For later, make it a toggle
			}
			else if (xbox2.DPad() == 0) { //High
				position = 2; //63
			}
			else if (xbox2.DPad() == 90){ //Middle
				position = 1;
			}
			Elevator.set(position);
		}
		else if (xbox2.LB() == true) { //Ball
			if (xbox2.DPad() == 180) { //Low
				position = 3; //For later, make it a toggle
			}
			else if (xbox2.DPad() == 0) { //High
				position = 5; //63
			}
			else if (xbox2.DPad() == 270) { //Middle
				position = 4;
			}
			Elevator.set(position);
		}
		else {
//			Elevator.setPower(xbox2.LStickY());
		}

		SmartDashboard.putNumber("DPad", xbox2.DPad());

		HatchIntake.set(xbox2.A(), xbox2.B());

		BallIntake.set(xbox2.X(), xbox2.Y());

		compressor.start();

//		SmartDashboard.putNumber("DistanceFR", SwerveDrivetrain.swerveModules.get(WheelType.FRONT_RIGHT).getDistance());
//		SmartDashboard.putNumber("DistanceFL", SwerveDrivetrain.swerveModules.get(WheelType.FRONT_LEFT).getDistance());
//		SmartDashboard.putNumber("DistanceBL", SwerveDrivetrain.swerveModules.get(WheelType.BACK_LEFT).getDistance());
//		SmartDashboard.putNumber("DistanceBR", SwerveDrivetrain.swerveModules.get(WheelType.BACK_RIGHT).getDistance());
//
//		SmartDashboard.putNumber("AngleFR", SwerveDrivetrain.swerveModules.get(WheelType.FRONT_RIGHT).getAngle());
//		SmartDashboard.putNumber("AngleFL", SwerveDrivetrain.swerveModules.get(WheelType.FRONT_LEFT).getAngle());
//		SmartDashboard.putNumber("AngleBL", SwerveDrivetrain.swerveModules.get(WheelType.BACK_LEFT).getAngle());
//		SmartDashboard.putNumber("AngleBR", SwerveDrivetrain.swerveModules.get(WheelType.BACK_RIGHT).getAngle());

		if (xbox1.Back()) {
			imu.reset(0); // robot should be perpendicular to field when pressed.
		} else if (xbox1.Y()) {
			imu.reset(180);
		} else if (xbox1.X()) {
			imu.reset(270);
		} else if (xbox1.B()) {
			imu.reset(90);
		}

		// forward command (-1.0 to 1.0)
//		if (Math.abs(xbox1.LStickY()) >= 0.5) {
			FWD = -xbox1.LStickY()/* / 10.5 * Ds.getBatteryVoltage() * 1.0*/;
//		}
//		System.out.println(FWD + "||" + STR + "||" + RCW);
		// strafe command (-1.0 to 1.0)
//		if (Math.abs(xbox1.LStickX()) >= 0.05) {
			STR = xbox1.LStickX() /*/ 10.5 * Ds.getBatteryVoltage() * 1.0*/;
//		}

		// Increase the time it takes for the robot to accelerate

		//Get pressure sensor to disable once above 100 psi
		//Base it off actuations
		if (FWD != 0.0 || STR != 0.0) {
//			compressor.start();
			FWD *= accel.calculate();
			STR *= accel.calculate();
			if (Math.abs(FWD) > 0.3 || Math.abs(STR) > 0.3) {
				decelFWD.set(prevFWD[cmdCounter], 0.0, 0.8);
				decelSTR.set( prevSTR[cmdCounter], 0.0, 0.8);
			}
		} else {
//			compressor.stop();
			accel.set(0.0, 1.0, 2);
			FWD = decelFWD.calculate();
			STR = decelSTR.calculate();
		}

			System.out.println(RCW + "||" + robotRotation + "||" + rcwAccel.calculate());


		prevRCW[cmdCounter] = RCW;
		prevFWD[cmdCounter] = FWD;
		prevSTR[cmdCounter] = STR;

		SmartDashboard.putNumber("PrevFWD", prevFWD[cmdCounter]);
		SmartDashboard.putNumber("Counter", cmdCounter);

		if (cmdCounter > 4) {
			cmdCounter = 0;
		}

//		System.out.println(STR);


		if (imu.collisionDetected()) {
			xbox1.rumbleRight(1.0);
			xbox1.rumbleLeft(1.0);
		} else {
			xbox1.stopRumble();
		}

		if (rumble) {
			xbox2.rumbleRight(0.5);
			xbox2.rumbleLeft(0.5);
			rumbleTime++;
			if (rumbleTime > 20) {
				rumble = false;
			}
		} else {
			xbox2.stopRumble();
		}

		SmartDashboard.putNumber("FWD", FWD);
		SmartDashboard.putNumber("STR", STR);
		SmartDashboard.putNumber("RCW", RCW);
		SmartDashboard.putNumber("IMU Angle", imu.getAngle());

		double headingDeg = imu.getAngle();
		double headingRad = Math.toRadians(headingDeg);

		currentOrientedButton = xbox1.A();
		if (currentOrientedButton && !previousOrientedButton) {
			fieldOriented = !fieldOriented;

		}
		previousOrientedButton = currentOrientedButton;

		SmartDashboard.putBoolean("Field Oriented", fieldOriented);

		if (fieldOriented) {//Fix field oriented
			Vector commands;
			commands = MathUtils.convertOrientation(headingRad, FWD, STR);
			FWD = commands.getY();
			STR = commands.getX();
		} else {
//			if (!robotBackwards) {
				FWD *= -1;
				STR *= -1;
//			}
		}

//		SmartDashboard.putBoolean("Field Oriented", fieldOriented);

//		SmartDashboard.putNumber("FWD", FWD);
//		SmartDashboard.putNumber("STR", STR);
//		SmartDashboard.putNumber("RCW", RCW);

		// rotate clockwise command (-1.0 to 1.0)
		// Limited to half speed because of wheel direction calculation issues when rotating quickly
		// Let robot rotate at full speed if it is not translating

		if (FWD + STR == 0.0) { //Change this, when robot nears 0 it rotates too slow
			//FIXME, BIG ISSUE! Robot turns in the direction RCW is when Ian rotates while driving
			RCW = xbox1.RStickX();
			if (RCW != 0.0) {
				RCW *=rcwAccel.calculate();
				SmartDashboard.putNumber("Robot Rotation 2", rcwAccel.calculate());
				if (Math.abs(RCW) > 1) {
					decelRCW.set(prevRCW[cmdCounter], 0.0, 2);
				}
			} else {
				rcwAccel.set(0.0, 1, 1);
				RCW = decelRCW.calculate();
			}
		} else {
			RCW = xbox1.RStickX()/2;
		}

//		SmartDashboard.putBoolean("Backwards", robotBackwards);
//		if (robotBackwards) {
//			driveTrain.drive(new Vector(-STR, FWD), -RCW); // x = str, y = fwd, rotation = rcw
//		} else {
//		if (xbox1.LTrig() != 0.0) {
//			FWD = 0.2;
//		} else {
//			FWD = 0.0;
//			STR = 0.0;
//		}
//		if (xbox1.RTrig() != 0.0) {
//			RCW = 0.1;
//		} else {
//			RCW = 0.0;
//		}
		keepAngle();
		driveTrain.drive(new Vector(-STR, FWD), RCW); // x = str, y = fwd, rotation = rcw
//		}

//		RRLogger.writeFromQueue();

	}

	public void robotPeriodic() {
		currentDistance += SwerveDrivetrain.getRobotDistance();
//		System.out.println("Robot Dist: " + SwerveDrivetrain.getRobotDistance());
		SmartDashboard.putNumber("Distance", currentDistance);

		if (xbox1.Start()) {
			driveTrain.resetWheels();
		}
	}

	public void disabledInit() {
//		jet.setDisabled();
		autoMove = 0;

		// When robot is turned on, disabledInit is called once
		if (disabled < 1) {
			System.out.println("Hello, I am Otto");
			disabled++;
		}

	}

	/**
	 * This function is called periodically during test mode
	 */
	public void testPeriodic() {
		// LABEL test
		double speed = (xbox1.RStickX() * 0.3);

		if (xbox1.DPad() != -1) {
			dx = xbox1.DPad();
		}

//		System.out.println("FL Angle: " + MathUtils.resolveDeg(SwerveDrivetrain.swerveModules.get(WheelType.FRONT_LEFT).getAngle()));
//		System.out.println("BL Angle: " + MathUtils.resolveDeg(SwerveDrivetrain.swerveModules.get(WheelType.BACK_LEFT).getAngle()));
//		System.out.println("BR Angle: " + MathUtils.resolveDeg(SwerveDrivetrain.swerveModules.get(WheelType.BACK_RIGHT).getAngle()));
//		System.out.println("FR Angle: " + MathUtils.resolveDeg(SwerveDrivetrain.swerveModules.get(WheelType.FRONT_RIGHT).getAngle()));

		// Move a single motor from the drivetrain depending on Dpad and right stick
//		if (dx == 0) {
//			SwerveDrivetrain.swerveModules.get(WheelType.FRONT_LEFT).setDirectTranslateCommand(speed);
//
//			SwerveDrivetrain.swerveModules.get(WheelType.FRONT_LEFT).setDirectRotateCommand(0);
//			SwerveDrivetrain.swerveModules.get(WheelType.BACK_LEFT).setDirectTranslateCommand(0);
//			SwerveDrivetrain.swerveModules.get(WheelType.BACK_LEFT).setDirectRotateCommand(0);
//			SwerveDrivetrain.swerveModules.get(WheelType.FRONT_RIGHT).setDirectTranslateCommand(0);
//			SwerveDrivetrain.swerveModules.get(WheelType.FRONT_RIGHT).setDirectRotateCommand(0);
//			SwerveDrivetrain.swerveModules.get(WheelType.BACK_RIGHT).setDirectTranslateCommand(0);
//			SwerveDrivetrain.swerveModules.get(WheelType.BACK_RIGHT).setDirectRotateCommand(0);
//		}
//
//		if (dx == 45) {
//			SwerveDrivetrain.swerveModules.get(WheelType.FRONT_LEFT).setDirectRotateCommand(speed);
//
//			SwerveDrivetrain.swerveModules.get(WheelType.FRONT_LEFT).setDirectTranslateCommand(0);
//			SwerveDrivetrain.swerveModules.get(WheelType.BACK_LEFT).setDirectTranslateCommand(0);
//			SwerveDrivetrain.swerveModules.get(WheelType.BACK_LEFT).setDirectRotateCommand(0);
//			SwerveDrivetrain.swerveModules.get(WheelType.FRONT_RIGHT).setDirectTranslateCommand(0);
//			SwerveDrivetrain.swerveModules.get(WheelType.FRONT_RIGHT).setDirectRotateCommand(0);
//			SwerveDrivetrain.swerveModules.get(WheelType.BACK_RIGHT).setDirectTranslateCommand(0);
//			SwerveDrivetrain.swerveModules.get(WheelType.BACK_RIGHT).setDirectRotateCommand(0);
//		}
//
//		if (dx == 90) {
//			SwerveDrivetrain.swerveModules.get(WheelType.BACK_LEFT).setDirectTranslateCommand(speed);
//
//			SwerveDrivetrain.swerveModules.get(WheelType.FRONT_LEFT).setDirectTranslateCommand(0);
//			SwerveDrivetrain.swerveModules.get(WheelType.FRONT_LEFT).setDirectRotateCommand(0);
//			SwerveDrivetrain.swerveModules.get(WheelType.BACK_LEFT).setDirectRotateCommand(0);
//			SwerveDrivetrain.swerveModules.get(WheelType.FRONT_RIGHT).setDirectTranslateCommand(0);
//			SwerveDrivetrain.swerveModules.get(WheelType.FRONT_RIGHT).setDirectRotateCommand(0);
//			SwerveDrivetrain.swerveModules.get(WheelType.BACK_RIGHT).setDirectTranslateCommand(0);
//			SwerveDrivetrain.swerveModules.get(WheelType.BACK_RIGHT).setDirectRotateCommand(0);
//		}
//
//		if (dx == 135) {
//			SwerveDrivetrain.swerveModules.get(WheelType.BACK_LEFT).setDirectRotateCommand(speed);
//
//			SwerveDrivetrain.swerveModules.get(WheelType.FRONT_LEFT).setDirectTranslateCommand(0);
//			SwerveDrivetrain.swerveModules.get(WheelType.FRONT_LEFT).setDirectRotateCommand(0);
//			SwerveDrivetrain.swerveModules.get(WheelType.BACK_LEFT).setDirectTranslateCommand(0);
//			SwerveDrivetrain.swerveModules.get(WheelType.FRONT_RIGHT).setDirectTranslateCommand(0);
//			SwerveDrivetrain.swerveModules.get(WheelType.FRONT_RIGHT).setDirectRotateCommand(0);
//			SwerveDrivetrain.swerveModules.get(WheelType.BACK_RIGHT).setDirectTranslateCommand(0);
//			SwerveDrivetrain.swerveModules.get(WheelType.BACK_RIGHT).setDirectRotateCommand(0);
//		}
//
//		if (dx == 180) {
//			SwerveDrivetrain.swerveModules.get(WheelType.FRONT_RIGHT).setDirectTranslateCommand(speed);
//
//			SwerveDrivetrain.swerveModules.get(WheelType.FRONT_LEFT).setDirectTranslateCommand(0);
//			SwerveDrivetrain.swerveModules.get(WheelType.FRONT_LEFT).setDirectRotateCommand(0);
//			SwerveDrivetrain.swerveModules.get(WheelType.BACK_LEFT).setDirectTranslateCommand(0);
//			SwerveDrivetrain.swerveModules.get(WheelType.BACK_LEFT).setDirectRotateCommand(0);
//			SwerveDrivetrain.swerveModules.get(WheelType.FRONT_RIGHT).setDirectRotateCommand(0);
//			SwerveDrivetrain.swerveModules.get(WheelType.BACK_RIGHT).setDirectTranslateCommand(0);
//			SwerveDrivetrain.swerveModules.get(WheelType.BACK_RIGHT).setDirectRotateCommand(0);
//		}
//
//		if (dx == 225) {
//			SwerveDrivetrain.swerveModules.get(WheelType.FRONT_RIGHT).setDirectRotateCommand(speed);
//
//			SwerveDrivetrain.swerveModules.get(WheelType.FRONT_LEFT).setDirectTranslateCommand(0);
//			SwerveDrivetrain.swerveModules.get(WheelType.FRONT_LEFT).setDirectRotateCommand(0);
//			SwerveDrivetrain.swerveModules.get(WheelType.BACK_LEFT).setDirectTranslateCommand(0);
//			SwerveDrivetrain.swerveModules.get(WheelType.BACK_LEFT).setDirectRotateCommand(0);
//			SwerveDrivetrain.swerveModules.get(WheelType.FRONT_RIGHT).setDirectTranslateCommand(0);
//			SwerveDrivetrain.swerveModules.get(WheelType.BACK_RIGHT).setDirectTranslateCommand(0);
//			SwerveDrivetrain.swerveModules.get(WheelType.BACK_RIGHT).setDirectRotateCommand(0);
//		}
//
//		if (dx == 270) {
//			SwerveDrivetrain.swerveModules.get(WheelType.BACK_RIGHT).setDirectTranslateCommand(speed);
//
//			SwerveDrivetrain.swerveModules.get(WheelType.FRONT_LEFT).setDirectTranslateCommand(0);
//			SwerveDrivetrain.swerveModules.get(WheelType.FRONT_LEFT).setDirectRotateCommand(0);
//			SwerveDrivetrain.swerveModules.get(WheelType.BACK_LEFT).setDirectTranslateCommand(0);
//			SwerveDrivetrain.swerveModules.get(WheelType.BACK_LEFT).setDirectRotateCommand(0);
//			SwerveDrivetrain.swerveModules.get(WheelType.FRONT_RIGHT).setDirectTranslateCommand(0);
//			SwerveDrivetrain.swerveModules.get(WheelType.FRONT_RIGHT).setDirectRotateCommand(0);
//			SwerveDrivetrain.swerveModules.get(WheelType.BACK_RIGHT).setDirectRotateCommand(0);
//		}
//
//		if (dx == 315) {
//			SwerveDrivetrain.swerveModules.get(WheelType.BACK_RIGHT).setDirectRotateCommand(speed);
//
//			SwerveDrivetrain.swerveModules.get(WheelType.FRONT_LEFT).setDirectTranslateCommand(0);
//			SwerveDrivetrain.swerveModules.get(WheelType.FRONT_LEFT).setDirectRotateCommand(0);
//			SwerveDrivetrain.swerveModules.get(WheelType.BACK_LEFT).setDirectTranslateCommand(0);
//			SwerveDrivetrain.swerveModules.get(WheelType.BACK_LEFT).setDirectRotateCommand(0);
//			SwerveDrivetrain.swerveModules.get(WheelType.FRONT_RIGHT).setDirectTranslateCommand(0);
//			SwerveDrivetrain.swerveModules.get(WheelType.FRONT_RIGHT).setDirectRotateCommand(0);
//			SwerveDrivetrain.swerveModules.get(WheelType.BACK_RIGHT).setDirectTranslateCommand(0);
//		}
	}
}
