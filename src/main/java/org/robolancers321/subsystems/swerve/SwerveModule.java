/* (C) Robolancers 2024 */
package org.robolancers321.subsystems.swerve;

import static org.robolancers321.Constants.Swerve.*;

import org.robolancers321.Robot;

import com.ctre.phoenix.sensors.CANCoder;
import com.revrobotics.CANSparkMax;
import com.revrobotics.CANSparkMaxLowLevel.MotorType;
import com.revrobotics.CANSparkMaxLowLevel.PeriodicFrame;
import com.revrobotics.RelativeEncoder;
import com.revrobotics.SparkMaxPIDController;
import com.revrobotics.CANSparkMax.ControlType;

import edu.wpi.first.math.MathUtil;
import edu.wpi.first.math.filter.Debouncer;
import edu.wpi.first.math.filter.Debouncer.DebounceType;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.kinematics.SwerveModulePosition;
import edu.wpi.first.math.kinematics.SwerveModuleState;
import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;

public class SwerveModule {
  private final String id;

  private final CANSparkMax driveMotor;
  private final CANSparkMax turnMotor;

  private final RelativeEncoder driveEncoder;
  private final RelativeEncoder turnRelEncoder;
  private final CANCoder turnAbsEncoder;

  private final SparkMaxPIDController driveController;
  private final SparkMaxPIDController turnController;

  // private final Debouncer idleStateDebouncer;

  private SwerveModuleState desiredState;

  public SwerveModule(ModuleConfig config) {
    this.id = config.id;

    this.driveMotor = new CANSparkMax(config.kDriveId, MotorType.kBrushless);
    this.turnMotor = new CANSparkMax(config.kTurnId, MotorType.kBrushless);

    this.driveEncoder = driveMotor.getEncoder();
    this.turnRelEncoder = turnMotor.getEncoder();
    this.turnAbsEncoder = new CANCoder(config.kTurnEncoderId);

    this.driveController = driveMotor.getPIDController();
    this.turnController = turnMotor.getPIDController();
        // new PIDController(Turn.kP, Turn.kI, Turn.kD);

    // this.idleStateDebouncer = new Debouncer(4 * Robot.kDefaultPeriod, DebounceType.kRising);

    configMotors(config.driveIsInverted, config.turnIsInverted);
    configEncoders(config.magOffsetDeg);
    configControllers();
    burnFlash();
  }

  public void periodic() {
    final var currState = getState();

    SmartDashboard.putNumber(id + " currVeloMetersPerSecond", currState.speedMetersPerSecond);

    SmartDashboard.putNumber(id + " currAngleDeg (relEncoder)", currState.angle.getDegrees());

    // TODO: check how well rel. & abs. match up, delete after
    SmartDashboard.putNumber(id + " currAngleDeg (absEncoder)", getAbsoluteEncoderAngle().getDegrees());

    SmartDashboard.putNumber(id + " turn output", turnMotor.getAppliedOutput());

    if(desiredState == null) return;
    
    SmartDashboard.putNumber(
      id + " targVeloMetersPerSecond",
      desiredState.speedMetersPerSecond);
    SmartDashboard.putNumber(
      id + " targAngleDeg", desiredState.angle.getDegrees());

    // final double turnOutput = turnController.calculate(currState.angle.getRadians());
    // turnMotor.set(-MathUtil.clamp(turnOutput, -1.0, 1.0));
  }

  public void setDesiredState(SwerveModuleState state) {
    // could re-seed relative encoder on first idle (state == desiredState) w/ debouncer
    // final var shouldReseed = idleStateDebouncer.calculate(state.equals(desiredState));
    // SmartDashboard.putBoolean("modules should reseed", shouldReseed);
    // if(shouldReseed) {
    //   turnRelEncoder.setPosition(MathUtil.angleModulus(turnAbsEncoder.getPosition()));
    // }
    final var optimizedState = state;
        // SwerveModuleState.optimize(
        //     state,
        //     getRelativeEncoderAngle());

    // addresses skew/drift at the module level
    // optimizedState.speedMetersPerSecond *=
    // state.angle.minus(getState().angle).getCos();

    driveController.setReference(
        optimizedState.speedMetersPerSecond, CANSparkMax.ControlType.kVelocity);
    // turnController.setSetpoint(optimizedState.angle.getRadians());
    turnController.setReference(
        optimizedState.angle.getRadians(), ControlType.kPosition);

    this.desiredState = optimizedState;
  }

  public void setDrivePIDFCoeffs(double p, double i, double d, double f) {
    this.driveController.setP(p);
    this.driveController.setI(i);
    this.driveController.setD(d);
    this.driveController.setFF(f);
  }

  public void setTurnPIDFCoeffs(double p, double i, double d, double f) {
    this.turnController.setP(p);
    this.turnController.setI(i);
    this.turnController.setD(d);
    this.turnController.setFF(f);
  }

  public SwerveModulePosition getPosition() {
    return new SwerveModulePosition(
        driveEncoder.getPosition(),
        getRelativeEncoderAngle());
  }

  public SwerveModuleState getState() {
    return new SwerveModuleState(
        driveEncoder.getVelocity(), //  * kRPMToMetersPerSecond,
        getRelativeEncoderAngle());
  }

  private Rotation2d getAbsoluteEncoderAngle() {
    return Rotation2d.fromRadians(MathUtil.angleModulus(turnAbsEncoder.getAbsolutePosition()));
  }

  private Rotation2d getRelativeEncoderAngle() {
    return Rotation2d.fromRadians(MathUtil.angleModulus(turnRelEncoder.getPosition()));
  }

  private void configMotors(boolean driveIsInverted, boolean turnIsInverted) {
    driveMotor.setInverted(driveIsInverted);
    turnMotor.setInverted(turnIsInverted);

    driveMotor.setIdleMode(CANSparkMax.IdleMode.kBrake);
    turnMotor.setIdleMode(CANSparkMax.IdleMode.kBrake);

    driveMotor.setSmartCurrentLimit(40);
    turnMotor.setSmartCurrentLimit(40);

    driveMotor.enableVoltageCompensation(12);
    turnMotor.enableVoltageCompensation(12);

    // lower CAN bus util for unused readings and keep pos/vel readings at default rate (50Hz)
    driveMotor.setPeriodicFramePeriod(PeriodicFrame.kStatus0, 65535);
    driveMotor.setPeriodicFramePeriod(PeriodicFrame.kStatus1, 20);
    driveMotor.setPeriodicFramePeriod(PeriodicFrame.kStatus2, 20);
    driveMotor.setPeriodicFramePeriod(PeriodicFrame.kStatus3, 65535);
    driveMotor.setPeriodicFramePeriod(PeriodicFrame.kStatus4, 65535);
    driveMotor.setPeriodicFramePeriod(PeriodicFrame.kStatus5, 65535);
    driveMotor.setPeriodicFramePeriod(PeriodicFrame.kStatus6, 65535);

    turnMotor.setPeriodicFramePeriod(PeriodicFrame.kStatus0, 65535);
    turnMotor.setPeriodicFramePeriod(PeriodicFrame.kStatus1, 65535);
    driveMotor.setPeriodicFramePeriod(PeriodicFrame.kStatus2, 20);
    turnMotor.setPeriodicFramePeriod(PeriodicFrame.kStatus3, 65535);
    turnMotor.setPeriodicFramePeriod(PeriodicFrame.kStatus4, 65535);
    turnMotor.setPeriodicFramePeriod(PeriodicFrame.kStatus5, 65535);
    turnMotor.setPeriodicFramePeriod(PeriodicFrame.kStatus6, 65535);
  }

  private void configEncoders(double magOffsetDeg) {
    final var config = kCANCoderConfig;
    config.magnetOffsetDegrees = magOffsetDeg;

    turnAbsEncoder.configAllSettings(config);

    turnRelEncoder.setPositionConversionFactor(kRotationsToRadians);
    turnRelEncoder.setPosition(getAbsoluteEncoderAngle().getRadians());

    driveEncoder.setVelocityConversionFactor(kRPMToMetersPerSecond);
  }

  private void configControllers() {
    setDrivePIDFCoeffs(Drive.kP, Drive.kI, Drive.kD, Drive.kFF);
    setTurnPIDFCoeffs(Turn.kP, Turn.kI, Turn.kD, Turn.kFF);
    // this.turnController.enableContinuousInput(-Math.PI, Math.PI);

    turnController.setPositionPIDWrappingEnabled(true);
    turnController.setPositionPIDWrappingMinInput(-Math.PI);
    turnController.setPositionPIDWrappingMaxInput(Math.PI);
  }

  private void burnFlash() {
    driveMotor.burnFlash();
    turnMotor.burnFlash();

    Timer.delay(0.2); // 200ms buffer (burnFlash() causes calls to be dropped for a bit)
  }
}
